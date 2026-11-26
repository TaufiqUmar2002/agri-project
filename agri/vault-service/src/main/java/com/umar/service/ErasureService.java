package com.umar.service;

import com.umar.audit.AuditRecorder;
import com.umar.blindindex.BlindIndexService;
import com.umar.document.ObjectStorageProvider;
import com.umar.entity.dataProtection.Document;
import com.umar.entity.dataProtection.RetentionClass;
import com.umar.entity.dataProtection.VaultToken;
import com.umar.entity.keyManagement.ErasureRegistry;
import com.umar.entity.keyManagement.LegalHold;
import com.umar.entity.keyManagement.SubjectKey;
import com.umar.events.VaultEventType;
import com.umar.exception.VaultApiException;
import com.umar.payload.request.vault.EraseSubjectRequest;
import com.umar.payload.response.vault.EraseSubjectResponse;
import com.umar.repository.dataProtection.DocumentRepository;
import com.umar.repository.dataProtection.RetentionClassRepository;
import com.umar.repository.dataProtection.VaultTokenRepository;
import com.umar.repository.keyManagement.ErasureRegistryRepository;
import com.umar.repository.keyManagement.SubjectKeyRepository;
import com.umar.repository.keyManagement.SubjectKeyServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;

/**
 * VLT-07 — Erase Subject by Crypto-Shredding.
 * Destroys subject keys for erasable groups, deletes lookup hashes & masked hints,
 * respects retention periods and legal holds, and re-applies erasure after restore.
 */
@Service
@RequiredArgsConstructor
public class ErasureService {

    private final ErasureRegistryRepository erasureRegistryRepository;
    private final SubjectKeyRepository subjectKeyRepository;
    private final SubjectKeyServiceImpl subjectKeyService;
    private final VaultTokenRepository tokenRepository;
    private final DocumentRepository documentRepository;
    private final RetentionClassRepository retentionClassRepository;
    private final BlindIndexService blindIndexService;
    private final ObjectStorageProvider objectStorageProvider;
    private final LegalHoldService legalHoldService;
    private final VaultEventEmitter vaultEventEmitter;
    private final AuditRecorder auditRecorder;

    @Transactional
    public EraseSubjectResponse eraseSubject(String subjectRef) {
        return eraseSubject(subjectRef, null);
    }

    @Transactional
    public EraseSubjectResponse eraseSubject(String subjectRef, EraseSubjectRequest request) {
        String subjectHash = sha256(subjectRef);
        String requestId = (request != null && request.getRequestId() != null && !request.getRequestId().isBlank())
                ? request.getRequestId()
                : "erase_" + UUID.randomUUID().toString().replace("-", "");
        boolean dryRun = request != null && Boolean.TRUE.equals(request.getDryRun());
        String reason = (request != null && request.getReason() != null) ? request.getReason() : "DATA_SUBJECT_REQUEST";

        // Idempotent replay for identical requestId
        if (!dryRun && request != null && request.getRequestId() != null) {
            Optional<ErasureRegistry> prior = erasureRegistryRepository.findFirstByRequestId(requestId);
            if (prior.isPresent()) {
                return buildReplayResponse(subjectRef, subjectHash, prior.get());
            }
        }

        List<SubjectKey> subjectKeys = subjectKeyRepository.findBySubjectRef(subjectRef);
        List<VaultToken> tokens = tokenRepository.findBySubjectRef(subjectRef);
        List<Document> docs = documentRepository.findBySubjectRef(subjectRef);

        if (subjectKeys.isEmpty() && tokens.isEmpty() && docs.isEmpty()) {
            // Backward-compatible behavior: if called with bare POST /subjects/{id}/erase without prior tokens,
            // still record erasable shredding if no request body; if explicit request body, check holdings or shred ERASABLE.
            if (request != null) {
                throw VaultApiException.notFound("SUBJECT_NOT_FOUND", "Subject not found");
            }
        }

        Set<String> targetGroups = new LinkedHashSet<>();
        if (request != null && request.getGroups() != null && !request.getGroups().isEmpty()) {
            request.getGroups().forEach(g -> targetGroups.add(g.trim().toUpperCase()));
        } else {
            subjectKeys.forEach(sk -> targetGroups.add(sk.getRetentionGroup().toUpperCase()));
            tokens.forEach(t -> targetGroups.add(
                    t.getRetentionGroup() != null ? t.getRetentionGroup().toUpperCase() : "ERASABLE"));
            docs.forEach(d -> targetGroups.add(
                    d.getRetentionGroup() != null ? d.getRetentionGroup().toUpperCase() : "ERASABLE"));
            if (targetGroups.isEmpty()) {
                targetGroups.add("ERASABLE");
            }
        }

        List<LegalHold> activeHolds = legalHoldService.findActiveHoldsForSubject(subjectRef);
        List<EraseSubjectResponse.GroupOutcome> groupOutcomes = new ArrayList<>();
        List<String> shreddedGroups = new ArrayList<>();
        List<String> retainedGroups = new ArrayList<>();
        List<String> onHoldGroups = new ArrayList<>();
        int totalShreddedItems = 0;
        UUID lastSavedId = UUID.randomUUID();
        String nowStr = Instant.now().toString();

        for (String group : targetGroups) {
            List<VaultToken> groupTokens = tokens.stream()
                    .filter(t -> group.equalsIgnoreCase(t.getRetentionGroup() != null ? t.getRetentionGroup() : "ERASABLE"))
                    .toList();
            List<Document> groupDocs = docs.stream()
                    .filter(d -> group.equalsIgnoreCase(d.getRetentionGroup() != null ? d.getRetentionGroup() : "ERASABLE"))
                    .toList();
            int itemsAffected = groupTokens.size() + groupDocs.size();

            boolean held = activeHolds.stream().anyMatch(h -> holdCoversGroup(h, group, groupTokens, groupDocs));
            if (held) {
                onHoldGroups.add(group);
                groupOutcomes.add(EraseSubjectResponse.GroupOutcome.builder()
                        .name(group)
                        .status("ON_HOLD")
                        .legalBasis("LEGAL_HOLD")
                        .itemsAffected(itemsAffected)
                        .build());
                continue;
            }

            if (isGroupRetained(group, groupDocs)) {
                RetentionClass rc = retentionClassRepository.findByGroup(group).orElse(null);
                String retainUntil = resolveRetainUntil(groupDocs, rc);
                String legalBasis = rc != null && rc.getLegalBasis() != null
                        ? rc.getLegalBasis().name()
                        : "LEGAL_OBLIGATION";
                retainedGroups.add(group);
                groupOutcomes.add(EraseSubjectResponse.GroupOutcome.builder()
                        .name(group)
                        .status("RETAINED")
                        .legalBasis(legalBasis)
                        .retainUntil(retainUntil)
                        .itemsAffected(itemsAffected)
                        .build());
                continue;
            }

            if (!dryRun) {
                subjectKeyService.shredSubjectKey(subjectRef, group);

                List<String> classes = new ArrayList<>();
                for (VaultToken t : groupTokens) {
                    classes.add(t.getDataClass());
                    t.setMaskedForm(null);
                    tokenRepository.save(t);
                }
                if (!classes.isEmpty()) {
                    blindIndexService.deleteForSubjectAndClasses(subjectRef, classes);
                }
                for (Document d : groupDocs) {
                    objectStorageProvider.deleteObjects(d.getObjectRef(), d.getReplicaObjectRef());
                    d.setEncryptedContent(null);
                    d.setNonce(null);
                    d.setWrappedDek(null);
                    documentRepository.save(d);
                }

                ErasureRegistry reg = erasureRegistryRepository.findBySubjectHashAndGroup(subjectHash, group)
                        .orElseGet(ErasureRegistry::new);
                if (reg.getId() == null) {
                    reg.setId(UUID.randomUUID());
                }
                reg.setSubjectHash(subjectHash);
                reg.setGroup(group);
                reg.setShreddedAt(nowStr);
                reg.setRequestId(requestId);
                reg.setReason(reason);
                lastSavedId = erasureRegistryRepository.save(reg).getId();
            }

            shreddedGroups.add(group);
            totalShreddedItems += itemsAffected;
            groupOutcomes.add(EraseSubjectResponse.GroupOutcome.builder()
                    .name(group)
                    .status("SHREDDED")
                    .itemsAffected(itemsAffected)
                    .build());
        }

        String outcome;
        if (!shreddedGroups.isEmpty() && retainedGroups.isEmpty() && onHoldGroups.isEmpty()) {
            outcome = "COMPLETED";
        } else if (!shreddedGroups.isEmpty()) {
            outcome = "PARTIAL";
        } else if (!onHoldGroups.isEmpty()) {
            outcome = "ON_HOLD";
        } else {
            outcome = "PARTIAL";
        }

        if (!dryRun) {
            auditRecorder.recordStrict("VAULT_ERASURE_COMPLETED", "ERASE_SUBJECT", null, null,
                    reason, requestId, subjectRef, Math.max(1, totalShreddedItems), "SUCCESS", lastSavedId, null);
            vaultEventEmitter.emit(VaultEventType.ERASURE_COMPLETED, Map.of(
                    "operation", "ERASE_SUBJECT",
                    "requestId", requestId,
                    "subjectRefHash", subjectHash,
                    "outcome", outcome,
                    "shreddedGroups", shreddedGroups,
                    "retainedGroups", retainedGroups,
                    "onHoldGroups", onHoldGroups,
                    "itemsShredded", totalShreddedItems));
        }

        return EraseSubjectResponse.builder()
                .id(lastSavedId)
                .subjectRef(subjectRef)
                .subjectHash(subjectHash)
                .shreddedAt(dryRun ? null : nowStr)
                .requestId(requestId)
                .outcome(outcome)
                .groups(groupOutcomes)
                .verifiedAt(dryRun ? null : Instant.now().toString())
                .dryRun(dryRun)
                .build();
    }

    @Transactional
    public EraseSubjectResponse recordErasureException(String subjectRef, String reason) {
        String subjectHash = sha256(subjectRef);
        String requestId = "exempt_" + UUID.randomUUID().toString().replace("-", "");

        ErasureRegistry record = new ErasureRegistry();
        record.setId(UUID.randomUUID());
        record.setSubjectHash(subjectHash);
        record.setGroup("EXEMPT");
        record.setShreddedAt(null);
        record.setRequestId(requestId);
        record.setReason(reason);

        ErasureRegistry saved = erasureRegistryRepository.save(record);
        vaultEventEmitter.emit(VaultEventType.ERASURE_COMPLETED, Map.of(
                "operation", "ERASURE_EXCEPTION",
                "subjectHash", subjectHash,
                "requestId", requestId,
                "reason", reason != null ? reason : "",
                "recordCount", 1));
        auditRecorder.recordStrict("VAULT_ERASURE_EXCEPTION", "ERASURE_EXCEPTION", null, null,
                "REGULATOR_ORDER", requestId, subjectRef, 1, "SUCCESS", saved.getId(), null);
        return EraseSubjectResponse.builder()
                .id(saved.getId())
                .subjectRef(subjectRef)
                .subjectHash(saved.getSubjectHash())
                .shreddedAt(saved.getShreddedAt())
                .requestId(saved.getRequestId())
                .outcome("COMPLETED")
                .build();
    }

    /**
     * Re-applies all shredded entries from {@link ErasureRegistry} onto active SubjectKeys
     * (mandatory blocking step after any backup restore - VLT-07, VLT-10).
     */
    @Transactional
    public int reapplyErasureRegistry() {
        List<ErasureRegistry> shreddedEntries = erasureRegistryRepository.findAll().stream()
                .filter(e -> e.getShreddedAt() != null)
                .toList();
        if (shreddedEntries.isEmpty()) {
            return 0;
        }
        Map<String, Set<String>> shreddedByHash = new HashMap<>();
        for (ErasureRegistry entry : shreddedEntries) {
            shreddedByHash.computeIfAbsent(entry.getSubjectHash(), k -> new HashSet<>())
                    .add(entry.getGroup().toUpperCase());
        }

        int reShredded = 0;
        for (SubjectKey sk : subjectKeyRepository.findByState("ACTIVE")) {
            String h = sha256(sk.getSubjectRef());
            Set<String> groups = shreddedByHash.get(h);
            if (groups != null && groups.contains(sk.getRetentionGroup().toUpperCase())) {
                sk.setState("SHREDDED");
                sk.setWrappedKey(null);
                sk.setShreddedAt(Instant.now());
                subjectKeyRepository.save(sk);
                blindIndexService.deleteAllForSubject(sk.getSubjectRef());
                reShredded++;
            }
        }
        return reShredded;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getSubjectInventory(String subjectRef) {
        String subjectHash = sha256(subjectRef);
        List<SubjectKey> keys = subjectKeyRepository.findBySubjectRef(subjectRef);
        List<VaultToken> tokens = tokenRepository.findBySubjectRef(subjectRef);
        List<Document> docs = documentRepository.findBySubjectRef(subjectRef);
        List<LegalHold> holds = legalHoldService.findActiveHoldsForSubject(subjectRef);

        Map<String, Object> inv = new LinkedHashMap<>();
        inv.put("subjectHash", subjectHash);
        inv.put("tokenCount", tokens.size());
        inv.put("documentCount", docs.size());
        inv.put("retentionGroups", keys.stream().map(sk -> Map.of(
                "group", sk.getRetentionGroup(),
                "state", sk.getState())).toList());
        inv.put("activeLegalHolds", holds.size());
        inv.put("dataClasses", tokens.stream().map(VaultToken::getDataClass).distinct().toList());
        return inv;
    }

    private boolean isGroupRetained(String group, List<Document> groupDocs) {
        if ("ERASABLE".equalsIgnoreCase(group)) {
            return false;
        }
        if (!groupDocs.isEmpty()) {
            LocalDate today = LocalDate.now(ZoneOffset.UTC);
            boolean anyUnexpired = groupDocs.stream().anyMatch(d -> {
                if (d.getRetainUntil() == null) return true;
                try {
                    return LocalDate.parse(d.getRetainUntil()).isAfter(today);
                } catch (Exception e) {
                    return true;
                }
            });
            if (anyUnexpired) return true;
        }
        RetentionClass rc = retentionClassRepository.findByGroup(group).orElse(null);
        return rc != null && rc.getPeriodDays() != null && rc.getPeriodDays() > 0;
    }

    private String resolveRetainUntil(List<Document> groupDocs, RetentionClass rc) {
        for (Document d : groupDocs) {
            if (d.getRetainUntil() != null) return d.getRetainUntil();
        }
        int days = (rc != null && rc.getPeriodDays() != null && rc.getPeriodDays() > 0) ? rc.getPeriodDays() : 2555;
        return LocalDate.now(ZoneOffset.UTC).plusDays(days).toString();
    }

    private boolean holdCoversGroup(LegalHold hold, String group, List<VaultToken> groupTokens, List<Document> groupDocs) {
        String scope = hold.getScope();
        if (scope == null) return false;
        for (Document d : groupDocs) {
            if (scope.contains(d.getToken())) return true;
        }
        if (!scope.contains("dataClasses")) {
            return true;
        }
        return groupTokens.stream().anyMatch(t -> scope.contains(t.getDataClass()))
                || groupDocs.stream().anyMatch(d -> scope.contains(d.getDataClass()));
    }

    private EraseSubjectResponse buildReplayResponse(String subjectRef, String subjectHash, ErasureRegistry existing) {
        return EraseSubjectResponse.builder()
                .id(existing.getId())
                .subjectRef(subjectRef)
                .subjectHash(subjectHash)
                .shreddedAt(existing.getShreddedAt())
                .requestId(existing.getRequestId())
                .outcome("COMPLETED")
                .dryRun(false)
                .build();
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to hash subject reference", e);
        }
    }
}

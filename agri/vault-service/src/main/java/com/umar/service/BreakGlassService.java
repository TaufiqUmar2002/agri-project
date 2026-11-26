package com.umar.service;

import com.umar.approval.ApprovalService;
import com.umar.audit.AuditRecorder;
import com.umar.authentication.VaultSecurityContext;
import com.umar.authentication.WorkloadIdentity;
import com.umar.config.EphemeralSubjectDek;
import com.umar.crypto.CryptoEngineService;
import com.umar.entity.auditAndAccess.BreakGlassRequest;
import com.umar.entity.dataProtection.VaultToken;
import com.umar.events.VaultEventType;
import com.umar.exception.VaultApiException;
import com.umar.payload.request.vault.BreakGlassUseRequest;
import com.umar.payload.request.vault.CreateBreakGlassRequest;
import com.umar.payload.response.vault.ApprovalResponse;
import com.umar.payload.response.vault.BreakGlassResponse;
import com.umar.payload.response.vault.DetokenizeResponse;
import com.umar.payload.response.vault.ResolvedValue;
import com.umar.payload.response.vault.TokenFailure;
import com.umar.repository.auditAndAccess.BreakGlassRequestRepository;
import com.umar.repository.dataProtection.VaultTokenRepository;
import com.umar.repository.keyManagement.DataKeyMaterial;
import com.umar.repository.keyManagement.SubjectKeyServiceImpl;
import com.umar.security.VaultRole;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * VLT-06 — Request and Execute Break-Glass Access.
 */
@Service
@RequiredArgsConstructor
public class BreakGlassService {

    private static final int MAX_DURATION_MINUTES = 240;
    private static final int MIN_DURATION_MINUTES = 15;
    private static final int MAX_RECORDS = 200;
    private static final int MAX_SUBJECTS = 50;
    private static final int MAX_REQUESTS_PER_WEEK = 3;

    private final BreakGlassRequestRepository breakGlassRequestRepository;
    private final VaultTokenRepository tokenRepository;
    private final SubjectKeyServiceImpl subjectKeyService;
    private final CryptoEngineService cryptoEngineService;
    private final ApprovalService approvalService;
    private final AccessSignalEmitter accessSignalEmitter;
    private final VaultEventEmitter vaultEventEmitter;
    private final AuditRecorder auditRecorder;
    private final ObjectMapper objectMapper;

    @Transactional
    public BreakGlassResponse createBreakGlassRequest(CreateBreakGlassRequest request) {
        return createBreakGlassRequest(request, true);
    }

    @Transactional
    public BreakGlassResponse createBreakGlassRequest(CreateBreakGlassRequest request, boolean preApproved) {
        String caller = resolveCaller();

        long weeklyCount = breakGlassRequestRepository.countByRequestedByAndCreatedAtAfter(
                caller, Instant.now().minus(7, ChronoUnit.DAYS));
        if (weeklyCount >= MAX_REQUESTS_PER_WEEK) {
            throw VaultApiException.rateLimited("More than 3 break-glass requests per requester per week", 3600);
        }

        if (request.getDurationMinutes() != null) {
            if (request.getDurationMinutes() < MIN_DURATION_MINUTES || request.getDurationMinutes() > MAX_DURATION_MINUTES) {
                throw VaultApiException.unprocessable("SCOPE_TOO_BROAD", "The requested scope is too broad",
                        Map.of("maxDurationMinutes", MAX_DURATION_MINUTES));
            }
        }
        if (request.getMaxRecords() != null && request.getMaxRecords() > MAX_RECORDS) {
            throw VaultApiException.unprocessable("SCOPE_TOO_BROAD", "The requested scope is too broad",
                    Map.of("maxRecords", MAX_RECORDS));
        }
        if (request.getSubjectRefs() != null && request.getSubjectRefs().size() > MAX_SUBJECTS) {
            throw VaultApiException.unprocessable("SCOPE_TOO_BROAD", "The requested scope is too broad",
                    Map.of("maxSubjects", MAX_SUBJECTS));
        }
        if (request.getSubjectRefs() != null && request.getSubjectRefs().contains(request.getInvestigatorRef())) {
            throw VaultApiException.unprocessable("CONFLICT_OF_INTEREST", "The investigator cannot use this request");
        }
        if (request.getReason() != null && request.getReason().trim().length() < 50) {
            throw VaultApiException.badRequest("VALIDATION_ERROR", "Reason must be at least 50 characters");
        }
        if ((request.getScope() == null || request.getScope().isBlank()) && request.getSubjectRefs() == null) {
            throw VaultApiException.badRequest("VALIDATION_ERROR", "Scope is required");
        }

        String scopeJson = resolveScopeJson(request);
        int duration = request.getDurationMinutes() != null ? request.getDurationMinutes() : 120;
        int maxRecs = request.getMaxRecords() != null ? request.getMaxRecords() : MAX_RECORDS;
        Instant now = Instant.now();
        String validUntil = (request.getValidUntil() != null && !request.getValidUntil().isBlank())
                ? request.getValidUntil()
                : now.plus(duration, ChronoUnit.MINUTES).toString();

        BreakGlassRequest entity = new BreakGlassRequest();
        entity.setId(UUID.randomUUID());
        entity.setIncidentRef(request.getIncidentRef());
        entity.setScope(scopeJson);
        entity.setInvestigatorRef(request.getInvestigatorRef());
        entity.setRequestedBy(caller);
        entity.setLegalBasis(request.getLegalBasis());
        entity.setReason(request.getReason());
        entity.setDurationMinutes(duration);
        entity.setMaxRecords(maxRecs);
        entity.setRecordsUsed(0);
        entity.setValidUntil(validUntil);
        entity.setReviewStatus("PENDING");
        entity.setReviewDueBy(now.plus(24, ChronoUnit.HOURS).toString());
        entity.setCreatedAt(now);

        if (preApproved) {
            entity.setStatus("ACTIVE");
            entity.setValidFrom(now.toString());
            BreakGlassRequest saved = breakGlassRequestRepository.save(entity);
            emitBreakGlass("ISSUED", saved);
            return toResponse(saved, List.of());
        }

        String subjectHash = AuditRecorder.hashIfPresent(entity.getId() + "|" + scopeJson + "|" + request.getInvestigatorRef());
        ApprovalResponse approval = approvalService.requestApproval(
                "BREAK_GLASS",
                entity.getId().toString(),
                subjectHash,
                List.of(VaultRole.SECURITY_OFFICER, VaultRole.COMPLIANCE_OFFICER),
                2,
                request.getInvestigatorRef());

        entity.setStatus("PENDING_APPROVAL");
        entity.setApprovalId(approval.getId());
        BreakGlassRequest saved = breakGlassRequestRepository.save(entity);
        emitBreakGlass("REQUESTED", saved);
        return toResponse(saved, List.of("SECURITY_OFFICER", "COMPLIANCE_OFFICER"));
    }

    @Transactional
    public DetokenizeResponse useBreakGlassGrant(UUID grantId, BreakGlassUseRequest request) {
        BreakGlassRequest grant = breakGlassRequestRepository.findById(grantId)
                .orElseThrow(() -> VaultApiException.notFound("GRANT_NOT_FOUND", "Break-glass grant not found"));

        if (!"ACTIVE".equalsIgnoreCase(grant.getStatus())) {
            throw VaultApiException.forbidden("FORBIDDEN", "Break-glass grant is not active");
        }
        if (grant.getValidUntil() != null && Instant.now().isAfter(Instant.parse(grant.getValidUntil()))) {
            grant.setStatus("EXPIRED");
            breakGlassRequestRepository.save(grant);
            throw VaultApiException.forbidden("FORBIDDEN", "Break-glass grant has expired");
        }

        String caller = resolveCaller();
        if (!"TEMP-SERVICE".equalsIgnoreCase(caller) && grant.getInvestigatorRef() != null
                && !grant.getInvestigatorRef().equalsIgnoreCase(caller)) {
            throw VaultApiException.forbidden("FORBIDDEN", "Only the designated investigator may use this grant");
        }

        List<String> tokens = request.getTokens() != null ? request.getTokens() : List.of();
        int used = grant.getRecordsUsed() != null ? grant.getRecordsUsed() : 0;
        int maxRecs = grant.getMaxRecords() != null ? grant.getMaxRecords() : MAX_RECORDS;
        if (used + tokens.size() > maxRecs) {
            throw VaultApiException.unprocessable("SCOPE_TOO_BROAD", "Exceeds grant maxRecords",
                    Map.of("maxRecords", maxRecs));
        }

        List<ResolvedValue> resolved = new ArrayList<>();
        List<TokenFailure> failures = new ArrayList<>();
        String requestId = "bg_use_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);

        auditRecorder.recordStrict("VAULT_BREAKGLASS_ACCESSED", "BREAK_GLASS:" + request.getOperation(),
                null, null, grant.getLegalBasis(), grant.getIncidentRef(), null,
                Math.max(1, tokens.size()), "SUCCESS", grant.getId(), null);

        for (String tok : tokens) {
            Optional<VaultToken> opt = tokenRepository.findByToken(tok);
            if (opt.isEmpty()) {
                failures.add(TokenFailure.builder().token(tok).code("TOKEN_NOT_FOUND").build());
                continue;
            }
            VaultToken vt = opt.get();
            if (subjectKeyService.isSubjectGroupShredded(vt.getSubjectRef(), vt.getRetentionGroup())) {
                failures.add(TokenFailure.builder().token(tok).code("SUBJECT_ERASED").build());
                continue;
            }
            try (EphemeralSubjectDek dek = unwrapDek(vt)) {
                String val = cryptoEngineService.decryptValue(
                        vt.getCiphertext(), vt.getNonce(), dek, vt.getDataClass(), vt.getSubjectRef(), vt.getToken());
                resolved.add(ResolvedValue.builder().token(tok).dataClass(vt.getDataClass()).value(val).build());
            } catch (Exception e) {
                failures.add(TokenFailure.builder().token(tok).code("DECRYPTION_FAILED").build());
            }
        }

        grant.setRecordsUsed(used + resolved.size());
        breakGlassRequestRepository.save(grant);
        emitBreakGlass("ACCESSED", grant);

        return DetokenizeResponse.builder()
                .values(resolved)
                .failures(failures)
                .requestId(requestId)
                .build();
    }

    @Transactional
    public BreakGlassResponse closeBreakGlassGrant(UUID grantId) {
        BreakGlassRequest grant = breakGlassRequestRepository.findById(grantId)
                .orElseThrow(() -> VaultApiException.notFound("GRANT_NOT_FOUND", "Break-glass grant not found"));
        grant.setStatus("CLOSED");
        grant.setReviewStatus("COMPLETED");
        BreakGlassRequest saved = breakGlassRequestRepository.save(grant);
        emitBreakGlass("CLOSED", saved);
        return toResponse(saved, List.of());
    }

    @EventListener
    @Transactional
    public void onApprovalResolved(ApprovalService.ApprovalResolvedEvent event) {
        if (!"BREAK_GLASS".equalsIgnoreCase(event.approval().getType())) {
            return;
        }
        try {
            UUID bgId = UUID.fromString(event.approval().getSubjectId());
            breakGlassRequestRepository.findById(bgId).ifPresent(entity -> {
                if ("APPROVED".equalsIgnoreCase(event.approval().getStatus())) {
                    Instant now = Instant.now();
                    int dur = entity.getDurationMinutes() != null ? entity.getDurationMinutes() : 120;
                    entity.setStatus("ACTIVE");
                    entity.setValidFrom(now.toString());
                    entity.setValidUntil(now.plus(dur, ChronoUnit.MINUTES).toString());
                    entity.setReviewDueBy(now.plus(dur, ChronoUnit.MINUTES).plus(24, ChronoUnit.HOURS).toString());
                    breakGlassRequestRepository.save(entity);
                    emitBreakGlass("APPROVED", entity);
                } else if ("REJECTED".equalsIgnoreCase(event.approval().getStatus())) {
                    entity.setStatus("REJECTED");
                    breakGlassRequestRepository.save(entity);
                }
            });
        } catch (Exception ignored) {
        }
    }

    private void emitBreakGlass(String action, BreakGlassRequest entity) {
        vaultEventEmitter.emit(VaultEventType.BREAKGLASS_USED, Map.of(
                "operation", "BREAK_GLASS",
                "requestId", entity.getId().toString(),
                "grantId", entity.getId().toString(),
                "incidentRef", entity.getIncidentRef(),
                "investigatorRefHash", AuditRecorder.hashIfPresent(entity.getInvestigatorRef()),
                "action", action,
                "recordsUsed", entity.getRecordsUsed() != null ? entity.getRecordsUsed() : 0));
        auditRecorder.record("VAULT_BREAKGLASS_USED", "BREAK_GLASS", null, null,
                entity.getLegalBasis(), entity.getIncidentRef(), null, 1, "SUCCESS", entity.getId(), null);
        accessSignalEmitter.emit("BREAK_GLASS:" + action, null, entity.getLegalBasis(), "SUCCESS", 1);
    }

    private EphemeralSubjectDek unwrapDek(VaultToken vt) {
        if (vt.getSubjectKeyId() != null) {
            DataKeyMaterial skm = subjectKeyService.getKeyByVersion(vt.getSubjectKeyId());
            return cryptoEngineService.unwrapDekWithSubjectKey(vt.getWrappedDek(), skm.key(), skm.keyVersionId());
        }
        return cryptoEngineService.unwrapDek(vt.getWrappedDek());
    }

    private String resolveScopeJson(CreateBreakGlassRequest request) {
        if (request.getScope() != null && !request.getScope().isBlank()) {
            return request.getScope();
        }
        try {
            Map<String, Object> m = new LinkedHashMap<>();
            if (request.getSubjectRefs() != null) m.put("subjectRefs", request.getSubjectRefs());
            if (request.getDataClasses() != null) m.put("dataClasses", request.getDataClasses());
            if (request.getOperations() != null) m.put("operations", request.getOperations());
            if (request.getMaxRecords() != null) m.put("maxRecords", request.getMaxRecords());
            return objectMapper.writeValueAsString(m);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String resolveCaller() {
        WorkloadIdentity identity = VaultSecurityContext.getWorkloadIdentity();
        return identity != null ? identity.serviceName() : "UNKNOWN";
    }

    private BreakGlassResponse toResponse(BreakGlassRequest entity, List<String> approvalsRequired) {
        Map<String, Object> grantMap = null;
        if ("ACTIVE".equalsIgnoreCase(entity.getStatus())) {
            grantMap = new LinkedHashMap<>();
            grantMap.put("grantId", entity.getId().toString());
            grantMap.put("validFrom", entity.getValidFrom());
            grantMap.put("validUntil", entity.getValidUntil());
            grantMap.put("maxRecords", entity.getMaxRecords());
            grantMap.put("recordsUsed", entity.getRecordsUsed());
        }
        return BreakGlassResponse.builder()
                .id(entity.getId())
                .requestId("bg_" + entity.getId().toString().replace("-", "").substring(0, 10))
                .incidentRef(entity.getIncidentRef())
                .scope(entity.getScope())
                .investigatorRef(entity.getInvestigatorRef())
                .status(entity.getStatus())
                .validUntil(entity.getValidUntil())
                .durationMinutes(entity.getDurationMinutes())
                .approvalId(entity.getApprovalId() != null ? entity.getApprovalId().toString() : null)
                .approvalsRequired(approvalsRequired)
                .grant(grantMap)
                .reviewDueBy(entity.getReviewDueBy())
                .build();
    }
}

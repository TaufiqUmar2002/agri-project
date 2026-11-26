package com.umar.service;

import com.umar.approval.ApprovalService;
import com.umar.audit.AuditRecorder;
import com.umar.entity.dataProtection.AccessPolicy;
import com.umar.entity.dataProtection.DataClass;
import com.umar.entity.dataProtection.RetentionClass;
import com.umar.enums.vault.DetokenizeMode;
import com.umar.enums.vault.LegalBasis;
import com.umar.enums.vault.RETENTION_GROUP;
import com.umar.enums.vault.ValueKind;
import com.umar.events.VaultEventType;
import com.umar.exception.VaultApiException;
import com.umar.hooks.DataClassDefinition;
import com.umar.payload.request.vault.CreatePolicyRequest;
import com.umar.payload.response.vault.ApprovalResponse;
import com.umar.payload.response.vault.PolicyResponse;
import com.umar.repository.dataProtection.AccessPolicyRepository;
import com.umar.repository.dataProtection.DataClassRepository;
import com.umar.repository.dataProtection.RetentionClassRepository;
import com.umar.security.VaultRole;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * VLT-12 — Define Data Classes, Retention Classes, and Access Policies.
 */
@Service
@RequiredArgsConstructor
public class PolicyManagementService {

    private static final Set<String> IDENTITY_IMAGE_CLASSES = Set.of("KYC_DOCUMENT", "SELFIE");

    private final AccessPolicyRepository accessPolicyRepository;
    private final DataClassRepository dataClassRepository;
    private final RetentionClassRepository retentionClassRepository;
    private final DataClassDefinitionService dataClassDefinitionService;
    private final ApprovalService approvalService;
    private final VaultEventEmitter vaultEventEmitter;
    private final AuditRecorder auditRecorder;

    @Transactional
    public PolicyResponse createPolicy(CreatePolicyRequest request) {
        return createPolicy(request, true);
    }

    @Transactional
    public PolicyResponse createPolicy(CreatePolicyRequest request, boolean preApproved) {
        String kind = request.getKind() != null ? request.getKind().trim().toUpperCase() : "ACCESS_POLICY";
        if ("DATA_CLASS".equals(kind)) {
            return handleDataClassDefinition(request);
        }
        if ("RETENTION_CLASS".equals(kind)) {
            return handleRetentionClassDefinition(request);
        }

        // ACCESS_POLICY (supports both flat CreatePolicyRequest and VLT-12 definition.policy map)
        Map<String, Object> defPolicy = extractSubMap(request.getDefinition(), "policy");
        String serviceIdentity = firstNonBlank(request.getServiceIdentity(), asString(defPolicy.get("serviceIdentity")));
        String dataClass = firstNonBlank(request.getDataClass(), asString(defPolicy.get("dataClass")));
        if (dataClass == null) {
            dataClass = asString(defPolicy.get("secretRef"));
        }
        List<String> purposes = request.getPurposes() != null ? request.getPurposes() : asStringList(defPolicy.get("purposes"));
        List<String> operations = request.getOperations() != null ? request.getOperations() : asStringList(defPolicy.get("operations"));

        if (serviceIdentity == null || dataClass == null) {
            throw VaultApiException.badRequest("VALIDATION_ERROR", "serviceIdentity and dataClass are required");
        }
        if (purposes == null || purposes.isEmpty()) {
            throw VaultApiException.unprocessable("PURPOSE_REQUIRED", "At least one purpose is required");
        }
        if (operations == null || operations.isEmpty()) {
            throw VaultApiException.badRequest("VALIDATION_ERROR", "At least one operation is required");
        }

        String normalizedClass = dataClass.trim().toUpperCase();
        // Hard rule: identity images are never detokenizable or readable by services
        if (IDENTITY_IMAGE_CLASSES.contains(normalizedClass)
                && operations.stream().anyMatch(op -> "DETOKENIZE".equalsIgnoreCase(op) || "READ_DOCUMENT".equalsIgnoreCase(op))) {
            throw VaultApiException.unprocessable("CLASS_FORBIDS_OPERATION",
                    "This operation is not allowed for this class");
        }

        Optional<AccessPolicy> priorOpt = accessPolicyRepository.findByServiceIdentityAndDataClass(
                serviceIdentity.trim().toUpperCase(), normalizedClass);
        String change = "NEW";
        List<String> diffSummary = new ArrayList<>();
        if (priorOpt.isPresent()) {
            AccessPolicy prior = priorOpt.get();
            boolean widened = (prior.getAllowedPurposes() == null || !new HashSet<>(prior.getAllowedPurposes()).containsAll(purposes))
                    || (prior.getOperation() == null || !new HashSet<>(prior.getOperation()).containsAll(operations));
            change = widened ? "WIDENING" : "NARROWING";
            diffSummary.add("change:" + change);
        } else {
            diffSummary.add("new_policy:" + serviceIdentity + ":" + normalizedClass);
        }

        String defHash = AuditRecorder.hashIfPresent(serviceIdentity + "|" + normalizedClass + "|" + purposes + "|" + operations);
        boolean needsApproval = !preApproved && ("NEW".equals(change) || "WIDENING".equals(change));

        AccessPolicy policy = new AccessPolicy();
        policy.setServiceIdentity(serviceIdentity.trim().toUpperCase());
        policy.setDataClass(normalizedClass);
        policy.setAllowedPurposes(new ArrayList<>(purposes));
        policy.setOperation(new ArrayList<>(operations));
        Integer rpm = request.getRequestsPerMinute() != null
                ? request.getRequestsPerMinute()
                : asInt(defPolicy.get("ratePerSecond")) != null ? asInt(defPolicy.get("ratePerSecond")) * 60 : null;
        Integer batch = request.getMaxBatchSize() != null ? request.getMaxBatchSize() : asInt(defPolicy.get("maxBatch"));
        policy.setRates(rpm != null ? String.valueOf(rpm) : "600");
        policy.setBatch(batch != null ? String.valueOf(batch) : "100");
        policy.setConditions(request.getConditions() != null ? request.getConditions() : "{}");
        policy.setKind(kind);
        policy.setPolicyName(request.getName() != null ? request.getName() : normalizedClass + "_POLICY");
        policy.setVersions(String.valueOf(request.getBaseVersion() != null ? request.getBaseVersion() + 1 : 1));
        policy.setDefinitionHash(defHash);
        if (request.getEffectiveFrom() != null && !request.getEffectiveFrom().isBlank()) {
            policy.setEffectiveFrom(Instant.parse(request.getEffectiveFrom()));
        }

        if (needsApproval) {
            policy.setStatus(false);
            policy.setPolicyStatus("PENDING_APPROVAL");
            AccessPolicy saved = accessPolicyRepository.save(policy);
            ApprovalResponse approval = approvalService.requestApproval(
                    "POLICY_CHANGE",
                    String.valueOf(saved.getId()),
                    defHash,
                    List.of(VaultRole.SECURITY_OFFICER, VaultRole.COMPLIANCE_OFFICER),
                    2);
            saved.setApprovalId(approval.getId());
            saved = accessPolicyRepository.save(saved);
            emitPolicyChanged(saved, change);
            return toResponse(saved, change, List.of("SECURITY_OFFICER", "COMPLIANCE_OFFICER"), diffSummary);
        }

        policy.setStatus(true);
        policy.setPolicyStatus("ACTIVE");
        AccessPolicy saved = accessPolicyRepository.save(policy);
        emitPolicyChanged(saved, change);
        return toResponse(saved, change, List.of(), diffSummary);
    }

    @Transactional(readOnly = true)
    public List<PolicyResponse> listPolicies() {
        return accessPolicyRepository.findAll().stream()
                .map(p -> toResponse(p, "EXISTING", List.of(), List.of()))
                .toList();
    }

    @EventListener
    @Transactional
    public void onApprovalResolved(ApprovalService.ApprovalResolvedEvent event) {
        if (!"POLICY_CHANGE".equalsIgnoreCase(event.approval().getType())) {
            return;
        }
        try {
            Long id = Long.parseLong(event.approval().getSubjectId());
            accessPolicyRepository.findById(id).ifPresent(p -> {
                if ("APPROVED".equalsIgnoreCase(event.approval().getStatus())) {
                    p.setStatus(true);
                    p.setPolicyStatus("ACTIVE");
                    accessPolicyRepository.save(p);
                    emitPolicyChanged(p, "ACTIVATED");
                } else if ("REJECTED".equalsIgnoreCase(event.approval().getStatus())) {
                    p.setStatus(false);
                    p.setPolicyStatus("REVOKED");
                    accessPolicyRepository.save(p);
                }
            });
        } catch (Exception ignored) {
        }
    }

    private PolicyResponse handleDataClassDefinition(CreatePolicyRequest request) {
        Map<String, Object> dcMap = extractSubMap(request.getDefinition(), "class");
        List<String> purposes = asStringList(dcMap.get("allowedPurposes"));
        if (purposes == null || purposes.isEmpty()) {
            throw VaultApiException.unprocessable("PURPOSE_REQUIRED", "At least one purpose is required");
        }
        DataClassDefinition def = DataClassDefinition.builder()
                .name(request.getName())
                .valueKind(dcMap.get("valueKind") != null
                        ? ValueKind.valueOf(dcMap.get("valueKind").toString().toUpperCase())
                        : ValueKind.VALUE)
                .classification(asString(dcMap.get("classification")))
                .retentionGroup(dcMap.get("retentionGroup") != null
                        ? RETENTION_GROUP.valueOf(dcMap.get("retentionGroup").toString().toUpperCase())
                        : RETENTION_GROUP.ERASABLE)
                .hasLookupHash(Boolean.TRUE.equals(dcMap.get("blindIndex")))
                .fpe(String.valueOf(Boolean.TRUE.equals(dcMap.get("fpe"))))
                .detokenizable(Boolean.FALSE.equals(dcMap.get("detokenizable")) ? DetokenizeMode.NO : DetokenizeMode.YES)
                .allowedPurposes(purposes)
                .version(request.getBaseVersion() != null ? request.getBaseVersion() + 1 : 1)
                .status("ACTIVE")
                .build();
        DataClass saved = dataClassDefinitionService.defineDataClass(def);
        String defHash = AuditRecorder.hashIfPresent(saved.getName() + "|v" + saved.getVersion());
        return PolicyResponse.builder()
                .kind("DATA_CLASS")
                .name(saved.getName())
                .version(saved.getVersion())
                .policyStatus(saved.getStatus())
                .change("NEW")
                .definitionHash(defHash)
                .effectiveFrom(request.getEffectiveFrom())
                .status(true)
                .build();
    }

    private PolicyResponse handleRetentionClassDefinition(CreatePolicyRequest request) {
        Map<String, Object> retMap = extractSubMap(request.getDefinition(), "retention");
        Integer periodDays = asInt(retMap.get("periodDays"));
        Integer minDays = asInt(retMap.get("minimumDays"));
        if (periodDays != null && minDays != null && periodDays < minDays) {
            throw VaultApiException.unprocessable("RETENTION_BELOW_MINIMUM",
                    "The retention period is too short", Map.of("minimumDays", minDays));
        }
        String groupStr = asString(retMap.get("group"));
        if (("KYC_RETAIN".equalsIgnoreCase(groupStr) || "FINANCIAL_RETAIN".equalsIgnoreCase(groupStr)
                || "EVIDENCE_RETAIN".equalsIgnoreCase(groupStr)) && periodDays != null && periodDays < 2555) {
            throw VaultApiException.unprocessable("RETENTION_BELOW_MINIMUM",
                    "The retention period is too short", Map.of("minimumDays", 2555));
        }
        RetentionClass rc = RetentionClass.builder()
                .id(UUID.randomUUID())
                .name(request.getName().toUpperCase())
                .group(groupStr != null ? groupStr.toUpperCase() : request.getName().toUpperCase())
                .periodDays(periodDays)
                .minimumDays(minDays)
                .legalBasis(LegalBasis.LEGAL_OBLIGATION)
                .status("ACTIVE")
                .build();
        retentionClassRepository.save(rc);
        return PolicyResponse.builder()
                .kind("RETENTION_CLASS")
                .name(rc.getName())
                .version(1)
                .policyStatus("ACTIVE")
                .change("NEW")
                .definitionHash(AuditRecorder.hashIfPresent(rc.getName()))
                .status(true)
                .build();
    }

    private void emitPolicyChanged(AccessPolicy saved, String change) {
        vaultEventEmitter.emit(VaultEventType.POLICY_CHANGED, Map.of(
                "operation", "CREATE_POLICY",
                "kind", saved.getKind() != null ? saved.getKind() : "ACCESS_POLICY",
                "name", saved.getPolicyName() != null ? saved.getPolicyName() : saved.getDataClass(),
                "change", change,
                "status", saved.getPolicyStatus() != null ? saved.getPolicyStatus() : "ACTIVE",
                "serviceIdentity", saved.getServiceIdentity(),
                "dataClass", saved.getDataClass(),
                "policyId", saved.getId().toString()));
        auditRecorder.recordStrict("VAULT_POLICY_CHANGED", "CREATE_POLICY", saved.getDataClass(), null,
                "GENERAL", saved.getId().toString(), null, 1, "SUCCESS", null, null);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractSubMap(Map<String, Object> def, String key) {
        if (def == null) return Map.of();
        Object sub = def.get(key);
        if (sub instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        return def;
    }

    private String asString(Object o) {
        return o == null ? null : o.toString();
    }

    private Integer asInt(Object o) {
        if (o instanceof Number n) return n.intValue();
        if (o instanceof String s) {
            try { return Integer.parseInt(s); } catch (Exception e) { return null; }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private List<String> asStringList(Object o) {
        if (o instanceof List<?> list) {
            return list.stream().map(Object::toString).toList();
        }
        return null;
    }

    private String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        if (b != null && !b.isBlank()) return b;
        return null;
    }

    private PolicyResponse toResponse(AccessPolicy policy, String change, List<String> approvalsRequired, List<String> diffSummary) {
        int ver = 1;
        try {
            if (policy.getVersions() != null) ver = Integer.parseInt(policy.getVersions());
        } catch (Exception ignored) {
        }
        return PolicyResponse.builder()
                .id(policy.getId())
                .serviceIdentity(policy.getServiceIdentity())
                .dataClass(policy.getDataClass())
                .purposes(policy.getAllowedPurposes())
                .operations(policy.getOperation())
                .requestsPerMinute(policy.getRates())
                .maxBatchSize(policy.getBatch())
                .conditions(policy.getConditions())
                .status(policy.isStatus())
                .createdAt(policy.getCreatedAt())
                .kind(policy.getKind() != null ? policy.getKind() : "ACCESS_POLICY")
                .name(policy.getPolicyName() != null ? policy.getPolicyName() : policy.getDataClass())
                .version(ver)
                .policyStatus(policy.getPolicyStatus() != null ? policy.getPolicyStatus() : (policy.isStatus() ? "ACTIVE" : "PENDING_APPROVAL"))
                .change(change)
                .definitionHash(policy.getDefinitionHash())
                .effectiveFrom(policy.getEffectiveFrom() != null ? policy.getEffectiveFrom().toString() : null)
                .approvalId(policy.getApprovalId() != null ? policy.getApprovalId().toString() : null)
                .approvalsRequired(approvalsRequired)
                .diffSummary(diffSummary)
                .build();
    }
}

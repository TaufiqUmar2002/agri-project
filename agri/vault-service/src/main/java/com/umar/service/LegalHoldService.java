package com.umar.service;

import com.umar.approval.ApprovalService;
import com.umar.audit.AuditRecorder;
import com.umar.entity.keyManagement.LegalHold;
import com.umar.entity.keyManagement.SubjectKey;
import com.umar.events.VaultEventType;
import com.umar.exception.VaultApiException;
import com.umar.payload.request.vault.CreateLegalHoldRequest;
import com.umar.payload.response.vault.ApprovalResponse;
import com.umar.payload.response.vault.LegalHoldResponse;
import com.umar.repository.keyManagement.LegalHoldRepository;
import com.umar.repository.keyManagement.SubjectKeyRepository;
import com.umar.security.VaultRole;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/**
 * VLT-08 — Place or Lift Legal Hold.
 */
@Service
@RequiredArgsConstructor
public class LegalHoldService {

    private final LegalHoldRepository legalHoldRepository;
    private final SubjectKeyRepository subjectKeyRepository;
    private final ApprovalService approvalService;
    private final VaultEventEmitter vaultEventEmitter;
    private final AuditRecorder auditRecorder;
    private final ObjectMapper objectMapper;

    @Transactional
    public LegalHoldResponse createLegalHold(CreateLegalHoldRequest request) {
        String action = request.getAction() != null ? request.getAction().trim().toUpperCase() : "PLACE";
        if ("LIFT".equals(action)) {
            if (request.getHoldId() == null) {
                throw VaultApiException.badRequest("VALIDATION_ERROR", "holdId is required when action is LIFT");
            }
            return requestLiftLegalHold(request.getHoldId(), request.getReason());
        }

        if ((request.getScope() == null || request.getScope().isBlank())
                && (request.getSubjectRefs() == null || request.getSubjectRefs().isEmpty())
                && (request.getDataClasses() == null || request.getDataClasses().isEmpty())
                && (request.getDocumentTokens() == null || request.getDocumentTokens().isEmpty())) {
            throw VaultApiException.badRequest("VALIDATION_ERROR", "Scope is required");
        }

        if (request.getSubjectRefs() != null && request.getSubjectRefs().size() > 5000
                && !"REGULATOR".equalsIgnoreCase(request.getAuthority())) {
            throw VaultApiException.unprocessable("SCOPE_TOO_BROAD", "The scope is too broad");
        }

        if (request.getReviewDate() != null && !request.getReviewDate().isBlank()) {
            try {
                LocalDate review = LocalDate.parse(request.getReviewDate());
                if (review.isAfter(LocalDate.now().plusMonths(12).plusDays(1))) {
                    throw VaultApiException.badRequest("VALIDATION_ERROR", "reviewDate must be within 12 months");
                }
            } catch (VaultApiException e) {
                throw e;
            } catch (Exception e) {
                throw VaultApiException.badRequest("VALIDATION_ERROR", "Invalid reviewDate format (ISO 8601 date required)");
            }
        }

        // Check if subjects in scope exist and are all shredded -> NOTHING_TO_HOLD
        if (request.getSubjectRefs() != null && !request.getSubjectRefs().isEmpty()) {
            boolean anyActive = false;
            boolean anyKnown = false;
            for (String subj : request.getSubjectRefs()) {
                List<SubjectKey> keys = subjectKeyRepository.findBySubjectRef(subj);
                if (!keys.isEmpty()) {
                    anyKnown = true;
                    if (keys.stream().anyMatch(k -> "ACTIVE".equalsIgnoreCase(k.getState()))) {
                        anyActive = true;
                        break;
                    }
                }
            }
            if (anyKnown && !anyActive) {
                throw VaultApiException.unprocessable("NOTHING_TO_HOLD", "There is nothing in this scope to hold");
            }
        }

        String scopeJson = buildScopeJson(request);
        String caseRefHash = AuditRecorder.hashIfPresent(request.getCaseRef());
        Instant now = Instant.now();

        LegalHold hold = new LegalHold();
        hold.setId(UUID.randomUUID());
        hold.setCaseRef(request.getCaseRef());
        hold.setCaseRefHash(caseRefHash);
        hold.setAuthority(request.getAuthority());
        hold.setScope(scopeJson);
        hold.setStatus("ACTIVE");
        hold.setReason(request.getReason());
        hold.setReviewDate(request.getReviewDate() != null ? request.getReviewDate() : LocalDate.now().plusMonths(6).toString());
        hold.setCreatedAt(now);

        LegalHold saved = legalHoldRepository.save(hold);
        String requestId = "vq_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);

        auditRecorder.recordStrict("VAULT_LEGALHOLD_CHANGED", "CREATE_LEGAL_HOLD", null, null,
                "GENERAL", request.getCaseRef(), null, 1, "SUCCESS", saved.getId(), null);
        vaultEventEmitter.emit(VaultEventType.LEGALHOLD_CHANGED, Map.of(
                "operation", "CREATE_LEGAL_HOLD",
                "action", "PLACED",
                "holdId", saved.getId().toString(),
                "caseRefHash", caseRefHash != null ? caseRefHash : "",
                "authority", request.getAuthority(),
                "scope", scopeJson));

        LegalHoldResponse resp = toResponse(saved);
        resp.setRequestId(requestId);
        return resp;
    }

    @Transactional
    public LegalHoldResponse requestLiftLegalHold(UUID holdId, String reason) {
        LegalHold hold = legalHoldRepository.findById(holdId)
                .orElseThrow(() -> VaultApiException.notFound("HOLD_NOT_FOUND", "Hold not found"));
        if (!"ACTIVE".equalsIgnoreCase(hold.getStatus())) {
            throw VaultApiException.conflict("HOLD_NOT_ACTIVE", "This hold is not active");
        }
        String subjectHash = AuditRecorder.hashIfPresent(hold.getId() + "|" + hold.getCaseRef());
        ApprovalResponse approval = approvalService.requestApproval(
                "LEGAL_HOLD_LIFT",
                hold.getId().toString(),
                subjectHash,
                List.of(VaultRole.COMPLIANCE_OFFICER),
                1);
        hold.setStatus("LIFT_PENDING_APPROVAL");
        hold.setApprovalId(approval.getId());
        if (reason != null) {
            hold.setReason(reason);
        }
        LegalHold saved = legalHoldRepository.save(hold);
        vaultEventEmitter.emit(VaultEventType.LEGALHOLD_CHANGED, Map.of(
                "operation", "LIFT_LEGAL_HOLD",
                "action", "LIFT_REQUESTED",
                "holdId", saved.getId().toString(),
                "caseRefHash", saved.getCaseRefHash() != null ? saved.getCaseRefHash() : ""));
        LegalHoldResponse resp = toResponse(saved);
        resp.setApprovalId(approval.getId().toString());
        return resp;
    }

    @Transactional
    public LegalHoldResponse liftLegalHold(UUID holdId) {
        LegalHold hold = legalHoldRepository.findById(holdId)
                .orElseThrow(() -> VaultApiException.notFound("HOLD_NOT_FOUND", "Hold not found"));
        if ("LIFTED".equalsIgnoreCase(hold.getStatus())) {
            throw VaultApiException.conflict("HOLD_NOT_ACTIVE", "This hold is not active");
        }
        hold.setStatus("LIFTED");
        hold.setLiftedAt(Instant.now());

        LegalHold saved = legalHoldRepository.save(hold);
        vaultEventEmitter.emit(VaultEventType.LEGALHOLD_CHANGED, Map.of(
                "operation", "LIFT_LEGAL_HOLD",
                "action", "LIFTED",
                "holdId", saved.getId().toString(),
                "caseRefHash", saved.getCaseRefHash() != null ? saved.getCaseRefHash() : "",
                "status", saved.getStatus()));
        auditRecorder.recordStrict("VAULT_LEGALHOLD_CHANGED", "LIFT_LEGAL_HOLD", null, null,
                "GENERAL", saved.getCaseRef(), null, 1, "SUCCESS", saved.getId(), null);
        return toResponse(saved);
    }

    @EventListener
    @Transactional
    public void onApprovalResolved(ApprovalService.ApprovalResolvedEvent event) {
        if (!"LEGAL_HOLD_LIFT".equalsIgnoreCase(event.approval().getType())) {
            return;
        }
        if ("APPROVED".equalsIgnoreCase(event.approval().getStatus())) {
            try {
                UUID holdId = UUID.fromString(event.approval().getSubjectId());
                legalHoldRepository.findById(holdId).ifPresent(hold -> {
                    if (!"LIFTED".equalsIgnoreCase(hold.getStatus())) {
                        liftLegalHold(holdId);
                    }
                });
            } catch (Exception ignored) {
            }
        }
    }

    @Transactional(readOnly = true)
    public boolean isSubjectHeld(String subjectRef) {
        return findActiveHoldsForSubject(subjectRef).stream().findAny().isPresent();
    }

    @Transactional(readOnly = true)
    public boolean isDocumentHeld(String documentToken, String subjectRef, String dataClass) {
        List<LegalHold> active = legalHoldRepository.findByStatusIn(List.of("ACTIVE", "LIFT_PENDING_APPROVAL"));
        for (LegalHold hold : active) {
            String scope = hold.getScope();
            if (scope == null) continue;
            if (documentToken != null && scope.contains(documentToken)) return true;
            if (subjectRef != null && scope.contains(subjectRef)) {
                if (dataClass == null || !scope.contains("dataClasses") || scope.contains(dataClass)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Transactional(readOnly = true)
    public List<LegalHold> findActiveHoldsForSubject(String subjectRef) {
        if (subjectRef == null || subjectRef.isBlank()) return List.of();
        return legalHoldRepository.findByStatusIn(List.of("ACTIVE", "LIFT_PENDING_APPROVAL")).stream()
                .filter(h -> h.getScope() != null && h.getScope().contains(subjectRef))
                .toList();
    }

    private String buildScopeJson(CreateLegalHoldRequest request) {
        if (request.getScope() != null && request.getScope().trim().startsWith("{")) {
            return request.getScope();
        }
        if (request.getSubjectRefs() != null || request.getDataClasses() != null || request.getDocumentTokens() != null) {
            try {
                Map<String, Object> m = new LinkedHashMap<>();
                if (request.getSubjectRefs() != null) m.put("subjectRefs", request.getSubjectRefs());
                if (request.getDataClasses() != null) m.put("dataClasses", request.getDataClasses());
                if (request.getDocumentTokens() != null) m.put("documentTokens", request.getDocumentTokens());
                return objectMapper.writeValueAsString(m);
            } catch (Exception ignored) {
            }
        }
        return request.getScope() != null ? request.getScope() : "{}";
    }

    private LegalHoldResponse toResponse(LegalHold hold) {
        return LegalHoldResponse.builder()
                .id(hold.getId())
                .holdId("hld_" + hold.getId().toString().replace("-", "").substring(0, 8))
                .caseRef(hold.getCaseRef())
                .authority(hold.getAuthority())
                .scope(hold.getScope())
                .status(hold.getStatus())
                .reviewDate(hold.getReviewDate())
                .approvalId(hold.getApprovalId() != null ? hold.getApprovalId().toString() : null)
                .createdAt(hold.getCreatedAt() != null ? hold.getCreatedAt().toString() : null)
                .build();
    }
}

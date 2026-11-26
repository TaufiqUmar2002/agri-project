package com.umar.approval;

import com.umar.audit.AuditRecorder;
import com.umar.entity.governance.Approval;
import com.umar.events.VaultEventType;
import com.umar.exception.VaultApiException;
import com.umar.payload.request.vault.ApprovalDecisionRequest;
import com.umar.payload.request.vault.ApprovalRequest;
import com.umar.payload.response.vault.ApprovalResponse;
import com.umar.security.RoleAuthorizationService;
import com.umar.security.VaultRole;
import com.umar.service.VaultEventEmitter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * VLT-13 — Decide Vault Approval with separation of duties, subjectHash binding,
 * checklist verification, and downstream release triggers.
 */
@Service
@RequiredArgsConstructor
public class ApprovalService {

    private static final Map<String, List<VaultRole>> TYPE_REQUIRED_ROLES = Map.of(
            "BREAK_GLASS", List.of(VaultRole.SECURITY_OFFICER, VaultRole.COMPLIANCE_OFFICER),
            "POLICY_CHANGE", List.of(VaultRole.SECURITY_OFFICER, VaultRole.COMPLIANCE_OFFICER),
            "KEY_ACTION", List.of(VaultRole.KEY_CUSTODIAN, VaultRole.KEY_CUSTODIAN),
            "KEY_ROTATION", List.of(VaultRole.KEY_CUSTODIAN, VaultRole.KEY_CUSTODIAN),
            "LEGAL_HOLD_LIFT", List.of(VaultRole.COMPLIANCE_OFFICER, VaultRole.COMPLIANCE_OFFICER),
            "ERASURE_EXCEPTION", List.of(VaultRole.COMPLIANCE_OFFICER, VaultRole.COMPLIANCE_OFFICER)
    );

    private static final Map<String, List<String>> TYPE_CHECKLIST_KEYS = Map.of(
            "BREAK_GLASS", List.of("incidentChecked", "scopeMinimal", "investigatorIndependent"),
            "KEY_ACTION", List.of("reasonChecked", "windowChecked", "rollbackReady"),
            "KEY_ROTATION", List.of("reasonChecked", "windowChecked", "rollbackReady"),
            "LEGAL_HOLD_LIFT", List.of("caseClosedConfirmed", "noOtherHolds"),
            "POLICY_CHANGE", List.of("purposeChecked", "leastPrivilege", "monitoringReady"),
            "ERASURE_EXCEPTION", List.of("legalBasisChecked")
    );

    private final ApprovalRepository approvalRepository;
    private final RoleAuthorizationService roleAuthorizationService;
    private final AuditRecorder auditRecorder;
    private final VaultEventEmitter vaultEventEmitter;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public ApprovalResponse requestApproval(ApprovalRequest request) {
        List<VaultRole> requiredRoles;
        if (request.getRequiredRoles() != null && !request.getRequiredRoles().isBlank()) {
            requiredRoles = Arrays.stream(request.getRequiredRoles().split(","))
                    .map(String::trim)
                    .map(this::toRole)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } else {
            requiredRoles = TYPE_REQUIRED_ROLES.getOrDefault(request.getType().toUpperCase(), List.of());
        }
        int requiredApprovals = request.getRequiredApprovals() != null && request.getRequiredApprovals() > 0
                ? request.getRequiredApprovals()
                : Math.max(1, requiredRoles.size());
        return requestApproval(request.getType(), request.getSubjectId(), request.getSubjectHash(),
                requiredRoles, requiredApprovals, null);
    }

    @Transactional
    public ApprovalResponse requestApproval(String type, String subjectId, String subjectHash,
                                            List<VaultRole> requiredRoles, int requiredApprovals) {
        return requestApproval(type, subjectId, subjectHash, requiredRoles, requiredApprovals, null);
    }

    @Transactional
    public ApprovalResponse requestApproval(String type, String subjectId, String subjectHash,
                                            List<VaultRole> requiredRoles, int requiredApprovals,
                                            String investigatorRef) {
        Approval approval = new Approval();
        approval.setId(UUID.randomUUID());
        approval.setType(type.toUpperCase());
        approval.setSubjectId(subjectId);
        approval.setSubjectHash(subjectHash);
        approval.setRequiredRoles(requiredRoles.stream().map(Enum::name).collect(Collectors.joining(",")));
        approval.setRequestedBy(roleAuthorizationService.getCaller());
        approval.setInvestigatorRef(investigatorRef);
        approval.setRequiredApprovals(Math.max(1, requiredApprovals));
        approval.setApprovalCount(0);
        approval.setStatus("PENDING");
        approval.setCreatedAt(Instant.now());
        if ("BREAK_GLASS".equalsIgnoreCase(type)) {
            approval.setExpiresAt(Instant.now().plus(2, ChronoUnit.HOURS));
        }

        Approval saved = approvalRepository.save(approval);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<ApprovalResponse> listApprovals(String status) {
        List<Approval> list = (status != null && !status.isBlank())
                ? approvalRepository.findByStatus(status.toUpperCase())
                : approvalRepository.findAll();
        return list.stream().map(this::toResponse).toList();
    }

    @Transactional
    public ApprovalResponse recordDecision(UUID approvalId, ApprovalDecisionRequest request) {
        Approval approval = approvalRepository.findById(approvalId)
                .orElseThrow(() -> VaultApiException.notFound("APPROVAL_NOT_FOUND", "Approval not found"));

        if (approval.getExpiresAt() != null && Instant.now().isAfter(approval.getExpiresAt())
                && "PENDING".equalsIgnoreCase(approval.getStatus())) {
            approval.setStatus("EXPIRED");
            approvalRepository.save(approval);
        }

        if (!"PENDING".equalsIgnoreCase(approval.getStatus())) {
            throw VaultApiException.conflict("APPROVAL_NOT_OPEN", "This approval is no longer open");
        }

        // Verify subjectHash matches what the approver reviewed
        if (request.getSubjectHash() != null && !request.getSubjectHash().isBlank()
                && approval.getSubjectHash() != null && !approval.getSubjectHash().isBlank()
                && !approval.getSubjectHash().equals(request.getSubjectHash())) {
            throw VaultApiException.conflict("SUBJECT_CHANGED", "The request changed. Please review again");
        }

        String approver = roleAuthorizationService.getCaller();

        // Separation of duties: requester and investigator may never approve.
        if (approver.equalsIgnoreCase(approval.getRequestedBy())
                || (approval.getInvestigatorRef() != null && approver.equalsIgnoreCase(approval.getInvestigatorRef()))) {
            throw VaultApiException.forbidden("REQUESTER_CANNOT_APPROVE", "You cannot approve this request");
        }

        List<VaultRole> required = parseRoles(approval.getRequiredRoles());
        if (!required.isEmpty()) {
            roleAuthorizationService.requireAnyRole(required.toArray(new VaultRole[0]));
        }

        List<String> decisions = approval.getDecisions() != null
                ? new ArrayList<>(approval.getDecisions())
                : new ArrayList<>();
        if (decisions.stream().anyMatch(d -> d.startsWith(approver + ":"))) {
            throw VaultApiException.conflict("ALREADY_DECIDED_BY_YOU", "You have already decided this approval");
        }

        boolean approve = "APPROVE".equalsIgnoreCase(request.getDecision());
        String noteText = request.getNote() != null ? request.getNote() : request.getComment();
        if (request.getNote() != null && request.getNote().trim().length() < 20) {
            throw VaultApiException.badRequest("VALIDATION_ERROR", "Approval note must be at least 20 characters");
        }

        if (approve && request.getChecklist() != null) {
            List<String> expectedKeys = TYPE_CHECKLIST_KEYS.getOrDefault(approval.getType(), List.of());
            for (String k : expectedKeys) {
                if (!Boolean.TRUE.equals(request.getChecklist().get(k))) {
                    throw VaultApiException.badRequest("VALIDATION_ERROR", "Checklist item '" + k + "' must be confirmed");
                }
            }
        }

        String decidedRole = resolveApproverRole(required, request.getApproverRole());
        decisions.add(approver + ":" + request.getDecision() + ":" + decidedRole);
        approval.setDecisions(decisions);
        approval.setNote(noteText);

        if (!approve) {
            approval.setDecision("REJECT");
            approval.setDecidedBy(approver);
            approval.setDecidedAt(Instant.now());
            approval.setStatus("REJECTED");
        } else {
            approval.setApprovalCount(approval.getApprovalCount() + 1);
            if (approval.getApprovalCount() >= approval.getRequiredApprovals()) {
                approval.setDecision("APPROVE");
                approval.setDecidedBy(approver);
                approval.setDecidedAt(Instant.now());
                approval.setStatus("APPROVED");
            }
        }

        Approval saved = approvalRepository.save(approval);
        auditRecorder.recordStrict("VAULT_APPROVAL_DECIDED", "APPROVAL_DECISION", null, null,
                "GENERAL", saved.getId().toString(), saved.getSubjectId(), 1, "SUCCESS", saved.getId(), null);

        if ("APPROVED".equals(saved.getStatus()) || "REJECTED".equals(saved.getStatus())) {
            eventPublisher.publishEvent(new ApprovalResolvedEvent(saved));
        }

        return toResponse(saved);
    }

    private String resolveApproverRole(List<VaultRole> required, String requestedRole) {
        if (requestedRole != null && !requestedRole.isBlank()) {
            return requestedRole.trim().toUpperCase();
        }
        Set<VaultRole> current = roleAuthorizationService.getCurrentRoles();
        for (VaultRole r : required) {
            if (current.contains(r)) {
                return r.name();
            }
        }
        return current.isEmpty() ? "APPROVER" : current.iterator().next().name();
    }

    private List<VaultRole> parseRoles(String requiredRoles) {
        if (requiredRoles == null || requiredRoles.isBlank()) {
            return List.of();
        }
        return Arrays.stream(requiredRoles.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(this::toRole)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private VaultRole toRole(String raw) {
        try {
            return VaultRole.valueOf(raw.trim().toUpperCase().replace('-', '_').replace(' ', '_'));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private ApprovalResponse toResponse(Approval approval) {
        List<VaultRole> required = parseRoles(approval.getRequiredRoles());
        List<String> decidedRoles = approval.getDecisions() == null ? List.of() : approval.getDecisions().stream()
                .map(d -> {
                    String[] parts = d.split(":");
                    return parts.length >= 3 ? parts[2] : "";
                })
                .filter(s -> !s.isEmpty())
                .toList();
        List<String> remaining = new ArrayList<>();
        if ("PENDING".equalsIgnoreCase(approval.getStatus())) {
            List<String> pool = new ArrayList<>(decidedRoles);
            for (VaultRole role : required) {
                if (!pool.remove(role.name())) {
                    remaining.add(role.name());
                }
            }
            if (remaining.isEmpty() && approval.getApprovalCount() < approval.getRequiredApprovals()) {
                for (int i = approval.getApprovalCount(); i < approval.getRequiredApprovals(); i++) {
                    remaining.add(required.isEmpty() ? "APPROVER" : required.get(0).name());
                }
            }
        }

        return ApprovalResponse.builder()
                .id(approval.getId())
                .approvalId(approval.getId().toString())
                .type(approval.getType())
                .subjectId(approval.getSubjectId())
                .subjectHash(approval.getSubjectHash())
                .status(approval.getStatus())
                .decision(approval.getDecision())
                .decidedBy(approval.getDecidedBy())
                .decidedAt(approval.getDecidedAt())
                .decisions(approval.getDecisions())
                .remaining(remaining)
                .subjectStatus("APPROVED".equals(approval.getStatus()) ? "ACTIVE"
                        : ("REJECTED".equals(approval.getStatus()) ? "REJECTED" : "PENDING_APPROVAL"))
                .requiredApprovals(approval.getRequiredApprovals())
                .approvalCount(approval.getApprovalCount())
                .build();
    }

    public record ApprovalResolvedEvent(Approval approval) {
    }
}

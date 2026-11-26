package com.umar.approval;

import com.umar.entity.governance.Approval;
import com.umar.exception.PolicyViolationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Enforces dual control (section 2.11): sensitive actions may only execute when
 * backed by an APPROVED {@link Approval} of the expected type. The approval must
 * have been decided by a different principal than the requester (enforced at
 * decision time in {@link ApprovalService#recordDecision}).
 */
@Service
@RequiredArgsConstructor
public class DualControlGuard {

    private final ApprovalRepository approvalRepository;

    /**
     * Verifies that {@code approvalId} references an APPROVED approval of
     * {@code expectedType}. Throws {@link PolicyViolationException} otherwise.
     */
    @Transactional(readOnly = true)
    public void requireApprovedApproval(String approvalId, String expectedType) {
        if (approvalId == null || approvalId.isBlank()) {
            throw new PolicyViolationException(
                    "Dual control required: a valid X-Approval-Id header referencing an APPROVED approval is required for " + expectedType);
        }

        UUID id;
        try {
            id = UUID.fromString(approvalId);
        } catch (IllegalArgumentException e) {
            throw new PolicyViolationException("Dual control required: invalid approval id '" + approvalId + "'");
        }

        Approval approval = approvalRepository.findById(id)
                .orElseThrow(() -> new PolicyViolationException("Dual control required: approval not found: " + approvalId));

        if (!"APPROVED".equalsIgnoreCase(approval.getStatus())) {
            throw new PolicyViolationException(String.format(
                    "Dual control required: approval %s is not APPROVED (status=%s)", approvalId, approval.getStatus()));
        }

        if (expectedType != null && !expectedType.equalsIgnoreCase(approval.getType())) {
            throw new PolicyViolationException(String.format(
                    "Dual control required: approval %s type '%s' does not match required type '%s'",
                    approvalId, approval.getType(), expectedType));
        }
    }
}

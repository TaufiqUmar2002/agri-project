package com.umar.payload.request.vault;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class ApprovalRequest {

    @NotBlank(message = "Approval type is required")
    private String type;

    private String subjectId;

    private String subjectHash;

    /** Optional comma-separated required roles; derived from type when omitted. */
    private String requiredRoles;

    /** Number of distinct approvals required before the request is APPROVED (N-of-M). Defaults to 1. */
    private Integer requiredApprovals;
}
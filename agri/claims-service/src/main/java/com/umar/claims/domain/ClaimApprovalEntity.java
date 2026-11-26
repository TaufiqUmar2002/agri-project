package com.umar.claims.domain;

import com.umar.enums.claims.ClaimDecisionAction;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "claim_approvals")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ClaimApprovalEntity {
    @Id
    @Column(name = "approval_id")
    private String approvalId;

    @Column(name = "claim_id", nullable = false)
    private String claimId;

    @Column(name = "retry_id")
    private String retryId;

    @Column(name = "approver_id", nullable = false)
    private String approverId;

    @Column(name = "approver_role", nullable = false)
    private String approverRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ClaimDecisionAction action;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String note;

    @Column(name = "decided_at", nullable = false)
    private Instant decidedAt;
}

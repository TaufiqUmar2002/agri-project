package com.umar.claims.domain;

import com.umar.enums.claims.ClaimDisputeReason;
import com.umar.enums.claims.DisputeOutcome;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "claim_disputes")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ClaimDisputeEntity {
    @Id
    @Column(name = "dispute_id")
    private String disputeId;

    @Column(name = "claim_id", nullable = false)
    private String claimId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason_code", nullable = false)
    private ClaimDisputeReason reasonCode;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String explanation;

    @Column(name = "photo_tokens_csv", columnDefinition = "TEXT")
    private String photoTokensCsv;

    @Column(nullable = false)
    private String status; // OPEN | PENDING_APPROVAL | RESOLVED

    @Enumerated(EnumType.STRING)
    private DisputeOutcome outcome;

    @Column(name = "reviewer_id")
    private String reviewerId;

    @Column(name = "first_approver_id")
    private String firstApproverId;

    @Column(name = "evidence_reviewed", nullable = false)
    private boolean evidenceReviewed;

    @Column(nullable = false)
    private boolean supplementary;

    @Column(name = "supplementary_amount_minor")
    private Long supplementaryAmountMinor;

    @Column(name = "supplementary_claim_id")
    private String supplementaryClaimId;

    @Column(name = "proposal_hash")
    private String proposalHash;

    @Column(name = "checks_json", columnDefinition = "TEXT")
    private String checksJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;
}

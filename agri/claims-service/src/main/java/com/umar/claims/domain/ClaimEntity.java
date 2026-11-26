package com.umar.claims.domain;

import com.umar.enums.claims.*;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "claims")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ClaimEntity {
    @Id
    @Column(name = "claim_id")
    private String claimId;

    @Column(name = "claim_number", nullable = false, unique = true)
    private String claimNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ClaimType type;

    @Column(name = "policy_id", nullable = false)
    private String policyId;

    @Column(name = "farmer_id", nullable = false)
    private String farmerId;

    @Column(name = "plot_id", nullable = false)
    private String plotId;

    @Column(name = "region_code", nullable = false)
    private String regionCode;

    @Column(name = "rule_id")
    private String ruleId;

    @Column(name = "rule_version")
    private Integer ruleVersion;

    @Column(name = "window_id")
    private String windowId;

    @Column(name = "tier_id")
    private String tierId;

    @Column(name = "decision_id")
    private String decisionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ClaimStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_path")
    private ApprovalPath approvalPath;

    @Column(name = "calculated_minor", nullable = false)
    private long calculatedMinor;

    @Column(name = "payable_minor", nullable = false)
    private long payableMinor;

    @Column(nullable = false)
    private String currency;

    @Column(name = "terms_hash")
    private String termsHash;

    @Column(name = "claim_hash", nullable = false)
    private String claimHash;

    @Column(name = "hold_reasons_csv", columnDefinition = "TEXT")
    private String holdReasonsCsv;

    @Enumerated(EnumType.STRING)
    @Column(name = "next_step")
    private NextStepAction nextStep;

    @Column(name = "current_attempt", nullable = false)
    private int currentAttempt;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;

    @Column(name = "cancel_requested", nullable = false)
    private boolean cancelRequested;

    @Column(name = "last_retry_at")
    private Instant lastRetryAt;

    @Column(name = "parent_claim_id")
    private String parentClaimId;

    @Column(name = "payout_id")
    private String payoutId;

    @Column(name = "payout_route")
    private String payoutRoute;

    @Column(name = "use_current_destination", nullable = false)
    private boolean useCurrentDestination;

    @Column(name = "destination_id")
    private String destinationId;

    @Column(name = "pending_retry_id")
    private String pendingRetryId;

    @Column(name = "hours_to_pay")
    private Double hoursToPay;

    @Column(name = "timeline_json", nullable = false, columnDefinition = "TEXT")
    private String timelineJson;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "dispute_window_ends")
    private Instant disputeWindowEnds;

    @Column(name = "dispute_count", nullable = false)
    private int disputeCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "prior_status")
    private ClaimStatus priorStatus;

    @Column(name = "version_num", nullable = false)
    private int versionNum;
}

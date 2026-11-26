package com.umar.payout.domain;

import com.umar.enums.payout.PayoutStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "payout_instructions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PayoutInstructionEntity {
    @Id
    @Column(name = "payout_id")
    private String payoutId;

    @Column(name = "claim_id", nullable = false)
    private String claimId;

    @Column(nullable = false)
    private int attempt;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    @Column(name = "originator_ref", nullable = false, unique = true)
    private String originatorRef;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PayoutStatus status;

    @Column(nullable = false)
    private boolean escalated;

    @Column(name = "cancel_requested", nullable = false)
    private boolean cancelRequested;

    @Column(nullable = false)
    private String route;

    private String connection;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(nullable = false)
    private String currency;

    @Column(name = "destination_token")
    private String destinationToken;

    @Column(name = "destination_mask")
    private String destinationMask;

    @Column(name = "provider_ref")
    private String providerRef;

    @Column(name = "provider_ref_masked")
    private String providerRefMasked;

    @Column(name = "attempts_json", nullable = false, columnDefinition = "TEXT")
    private String attemptsJson;

    @Column(name = "fallback_count", nullable = false)
    private int fallbackCount;

    @Column(name = "reason_code")
    private String reasonCode;

    private Boolean retryable;

    @Column(name = "hold_reason")
    private String holdReason;

    @Column(name = "cash_out_agent_id")
    private String cashOutAgentId;

    @Column(name = "cash_out_area")
    private String cashOutArea;

    @Column(name = "voucher_hash")
    private String voucherHash;

    @Column(name = "voucher_status")
    private String voucherStatus;

    @Column(name = "voucher_expires_at")
    private Instant voucherExpiresAt;

    @Column(name = "statement_match_status")
    private String statementMatchStatus;

    @Column(name = "statement_date")
    private LocalDate statementDate;

    @Column(name = "next_step")
    private String nextStep;

    @Column(name = "next_check_at")
    private Instant nextCheckAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "initiated_at")
    private Instant initiatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "hours_to_result")
    private Double hoursToResult;

    @Column(name = "version_num", nullable = false)
    private int versionNum;
}

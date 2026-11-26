package com.umar.claims.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "claim_reconciliation_breaks")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ReconciliationBreakEntity {
    @Id
    @Column(name = "break_id")
    private String breakId;

    @Column(nullable = false)
    private String type;

    @Column(nullable = false)
    private String severity;

    @Column(name = "claim_id")
    private String claimId;

    @Column(name = "payout_id")
    private String payoutId;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private String owner;

    @Column(name = "region_code")
    private String regionCode;

    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;
}

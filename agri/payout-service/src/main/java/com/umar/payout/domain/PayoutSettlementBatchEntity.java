package com.umar.payout.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "payout_settlement_batches")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PayoutSettlementBatchEntity {
    @Id
    @Column(name = "batch_id")
    private String batchId;

    @Column(nullable = false)
    private String connection;

    @Column(name = "settlement_date", nullable = false)
    private LocalDate settlementDate;

    @Column(nullable = false)
    private String currency;

    @Column(name = "gross_minor", nullable = false)
    private long grossMinor;

    @Column(name = "fees_minor", nullable = false)
    private long feesMinor;

    @Column(name = "net_minor", nullable = false)
    private long netMinor;

    @Column(nullable = false)
    private String status;

    @Column(name = "matched_count", nullable = false)
    private int matchedCount;

    @Column(name = "unmatched_count", nullable = false)
    private int unmatchedCount;

    @Column(nullable = false)
    private String checksum;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}

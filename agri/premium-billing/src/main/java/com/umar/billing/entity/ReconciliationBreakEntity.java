package com.umar.billing.entity;

import com.umar.enums.billing.BreakSeverity;
import com.umar.enums.billing.BreakType;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "reconciliation_breaks")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ReconciliationBreakEntity {
    @Id
    @Column(name = "break_id", length = 40)
    private String breakId;
    @Column(name = "run_id", nullable = false, length = 48)
    private String runId;
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 32)
    private BreakType type;
    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 16)
    private BreakSeverity severity;
    @Column(name = "status", nullable = false, length = 24)
    private String status; // OPEN | RESOLVED_AUTO | SUSPENSE
    @Column(name = "provider_txn_id", length = 80)
    private String providerTxnId;
    @Column(name = "payment_id", length = 40)
    private String paymentId;
    @Column(name = "amount_minor", nullable = false)
    private Long amountMinor;
    @Column(name = "detail", columnDefinition = "TEXT")
    private String detail;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}

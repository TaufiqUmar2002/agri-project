package com.umar.billing.entity;

import com.umar.enums.billing.InvoiceStatus;
import com.umar.enums.billing.InvoiceType;
import com.umar.enums.policy.PayerType;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "invoices")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class InvoiceEntity {
    @Id
    @Column(name = "invoice_id", length = 40)
    private String invoiceId;
    @Column(name = "invoice_number", nullable = false, unique = true, length = 40)
    private String invoiceNumber;
    @Column(name = "payment_reference", nullable = false, unique = true, length = 32)
    private String paymentReference;
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 24)
    private InvoiceType type;
    @Column(name = "policy_id", length = 40)
    private String policyId;
    @Column(name = "batch_id", length = 40)
    private String batchId;
    @Enumerated(EnumType.STRING)
    @Column(name = "payer_type", nullable = false, length = 16)
    private PayerType payerType;
    @Column(name = "payer_ref", nullable = false, length = 64)
    private String payerRef;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private InvoiceStatus status;
    @Column(name = "total_minor", nullable = false)
    private Long totalMinor;
    @Column(name = "premium_minor", nullable = false)
    private Long premiumMinor;
    @Column(name = "paid_minor", nullable = false)
    @Builder.Default
    private Long paidMinor = 0L;
    @Column(name = "outstanding_minor", nullable = false)
    private Long outstandingMinor;
    @Column(name = "currency", nullable = false, length = 8)
    private String currency;
    @Column(name = "subsidy_minor", nullable = false)
    @Builder.Default
    private Long subsidyMinor = 0L;
    @Column(name = "due_date")
    private Instant dueDate;
    @Column(name = "grace_ends_at")
    private Instant graceEndsAt;
    @Column(name = "instalment_plan_id", length = 40)
    private String instalmentPlanId;
    @Column(name = "plan_hash", length = 64)
    private String planHash;
    @Column(name = "activation_threshold_percent", nullable = false)
    @Builder.Default
    private Double activationThresholdPercent = 100.0;
    @Version
    @Column(name = "version", nullable = false)
    private Long version;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}

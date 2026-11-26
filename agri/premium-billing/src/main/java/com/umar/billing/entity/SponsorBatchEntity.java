package com.umar.billing.entity;

import com.umar.enums.billing.BatchAllocationMode;
import com.umar.enums.billing.PaymentMethod;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "sponsor_batches")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class SponsorBatchEntity {
    @Id
    @Column(name = "batch_id", length = 40)
    private String batchId;
    @Column(name = "batch_reference", nullable = false, unique = true, length = 40)
    private String batchReference;
    @Column(name = "sponsor_id", nullable = false, length = 40)
    private String sponsorId;
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 24)
    private PaymentMethod paymentMethod;
    @Enumerated(EnumType.STRING)
    @Column(name = "allocation_mode", nullable = false, length = 24)
    private BatchAllocationMode allocationMode;
    @Column(name = "consolidated_invoice_id", length = 40)
    private String consolidatedInvoiceId;
    @Column(name = "policy_ids_csv", nullable = false, columnDefinition = "TEXT")
    private String policyIdsCsv;
    @Column(name = "total_minor", nullable = false)
    private Long totalMinor;
    @Column(name = "currency", nullable = false, length = 8)
    private String currency;
    @Column(name = "status", nullable = false, length = 24)
    private String status; // OPEN | COVERED_ON_CREDIT | SETTLED
    @Column(name = "due_date", nullable = false)
    private Instant dueDate;
    @Column(name = "external_batch_ref", length = 60)
    private String externalBatchRef;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}

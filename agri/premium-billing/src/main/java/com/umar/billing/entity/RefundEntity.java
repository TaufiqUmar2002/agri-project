package com.umar.billing.entity;

import com.umar.enums.billing.RefundReasonCode;
import com.umar.enums.billing.RefundStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "refunds")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class RefundEntity {
    @Id
    @Column(name = "refund_id", length = 40)
    private String refundId;
    @Column(name = "invoice_id", nullable = false, length = 40)
    private String invoiceId;
    @Column(name = "policy_id", length = 40)
    private String policyId;
    @Column(name = "payment_ids_csv", columnDefinition = "TEXT")
    private String paymentIdsCsv;
    @Column(name = "amount_minor", nullable = false)
    private Long amountMinor;
    @Column(name = "currency", nullable = false, length = 8)
    private String currency;
    @Enumerated(EnumType.STRING)
    @Column(name = "reason_code", nullable = false, length = 32)
    private RefundReasonCode reasonCode;
    @Column(name = "source_event_id", unique = true, length = 80)
    private String sourceEventId;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private RefundStatus status;
    @Column(name = "destination_type", nullable = false, length = 24)
    private String destinationType;
    @Column(name = "destination_token", length = 80)
    private String destinationToken;
    @Column(name = "masked_destination", length = 64)
    private String maskedDestination;
    @Column(name = "provider", length = 32)
    private String provider;
    @Column(name = "provider_ref", length = 64)
    private String providerRef;
    @Column(name = "approval_id", length = 40)
    private String approvalId;
    @Column(name = "requested_by", nullable = false, length = 64)
    private String requestedBy;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}

package com.umar.billing.entity;

import com.umar.enums.billing.PaymentMethod;
import com.umar.enums.billing.PaymentSource;
import com.umar.enums.billing.PaymentStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "payments")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class PaymentEntity {
    @Id
    @Column(name = "payment_id", length = 40)
    private String paymentId;
    @Column(name = "invoice_id", nullable = false, length = 40)
    private String invoiceId;
    @Column(name = "attempt_id", length = 40)
    private String attemptId;
    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false, length = 24)
    private PaymentMethod method;
    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 16)
    private PaymentSource source;
    @Column(name = "amount_minor", nullable = false)
    private Long amountMinor;
    @Column(name = "currency", nullable = false, length = 8)
    private String currency;
    @Column(name = "payer_token", length = 80)
    private String payerToken;
    @Column(name = "masked_payer", length = 40)
    private String maskedPayer;
    @Column(name = "receipt_number", length = 64)
    private String receiptNumber;
    @Column(name = "statement_line_ref", length = 64)
    private String statementLineRef;
    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;
    @Column(name = "provider", length = 32)
    private String provider;
    @Column(name = "provider_txn_id", length = 80)
    private String providerTxnId;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private PaymentStatus status;
    @Column(name = "recorded_by", length = 64)
    private String recordedBy;
}

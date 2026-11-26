package com.umar.billing.entity;

import com.umar.enums.billing.PaymentAttemptStatus;
import com.umar.enums.billing.PaymentMethod;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "payment_attempts")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class PaymentAttemptEntity {
    @Id
    @Column(name = "attempt_id", length = 40)
    private String attemptId;
    @Column(name = "invoice_id", nullable = false, length = 40)
    private String invoiceId;
    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false, length = 24)
    private PaymentMethod method;
    @Column(name = "provider", length = 32)
    private String provider;
    @Column(name = "payer_number_token", length = 80)
    private String payerNumberToken;
    @Column(name = "masked_payer", length = 40)
    private String maskedPayer;
    @Column(name = "amount_minor", nullable = false)
    private Long amountMinor;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private PaymentAttemptStatus status;
    @Column(name = "provider_ref", length = 64)
    private String providerRef;
    @Column(name = "idempotency_key", length = 128)
    private String idempotencyKey;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}

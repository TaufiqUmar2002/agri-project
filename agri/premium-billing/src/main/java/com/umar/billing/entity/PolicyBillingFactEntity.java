package com.umar.billing.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "policy_billing_facts")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class PolicyBillingFactEntity {
    @Id
    @Column(name = "policy_id", length = 40)
    private String policyId;
    @Column(name = "farmer_id", nullable = false, length = 64)
    private String farmerId;
    @Column(name = "product_id", nullable = false, length = 40)
    private String productId;
    @Column(name = "premium_minor", nullable = false)
    private Long premiumMinor;
    @Column(name = "subsidy_minor", nullable = false)
    private Long subsidyMinor;
    @Column(name = "currency", nullable = false, length = 8)
    private String currency;
    @Column(name = "payer_type", nullable = false, length = 16)
    private String payerType;
    @Column(name = "payer_ref", length = 64)
    private String payerRef;
    @Column(name = "pay_by")
    private Instant payBy;
    @Column(name = "status", nullable = false, length = 24)
    private String status;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}

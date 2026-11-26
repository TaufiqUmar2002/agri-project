package com.umar.claims.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "claim_overpayment_cases")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OverpaymentCaseEntity {
    @Id
    @Column(name = "case_id")
    private String caseId;

    @Column(name = "claim_id", nullable = false)
    private String claimId;

    @Column(name = "policy_id", nullable = false)
    private String policyId;

    @Column(name = "paid_minor", nullable = false)
    private long paidMinor;

    @Column(name = "revised_payable_minor", nullable = false)
    private long revisedPayableMinor;

    @Column(name = "excess_minor", nullable = false)
    private long excessMinor;

    @Column(name = "reason_code", nullable = false)
    private String reasonCode;

    @Column(nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}

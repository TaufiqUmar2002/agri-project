package com.umar.riskpool.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity @Table(name = "pool_premium_register")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PolicyPremiumRegisterEntity {
    @Id @Column(name = "policy_id") private String policyId;
    @Column(name = "pool_id", nullable = false) private String poolId;
    @Column(name = "product_id", nullable = false) private String productId;
    @Column(name = "region_code", nullable = false) private String regionCode;
    @Column(nullable = false) private String peril;
    @Column(nullable = false) private String season;
    @Column(name = "sum_insured_minor", nullable = false) private long sumInsuredMinor;
    @Column(name = "written_minor", nullable = false) private long writtenMinor;
    @Column(name = "subsidy_minor", nullable = false) private long subsidyMinor;
    @Column(name = "paid_minor", nullable = false) private long paidMinor;
    @Column(name = "earned_minor", nullable = false) private long earnedMinor;
    @Column(name = "refunded_minor", nullable = false) private long refundedMinor;
    @Column(nullable = false) private String currency;
    @Column(name = "in_force", nullable = false) private boolean inForce;
    @Column(name = "coverage_start") private LocalDate coverageStart;
    @Column(name = "coverage_end") private LocalDate coverageEnd;
}

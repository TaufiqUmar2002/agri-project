package com.umar.trigger.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "trigger_policy_bindings")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PolicyBindingEntity {
    @Id
    @Column(name = "policy_id")
    private String policyId;

    @Column(name = "farmer_id", nullable = false)
    private String farmerId;

    @Column(name = "plot_id", nullable = false)
    private String plotId;

    @Column(name = "region_code", nullable = false)
    private String regionCode;

    @Column(name = "product_id", nullable = false)
    private String productId;

    @Column(name = "rule_id", nullable = false)
    private String ruleId;

    @Column(name = "rule_version", nullable = false)
    private Integer ruleVersion;

    @Column(name = "sum_insured_minor", nullable = false)
    private long sumInsuredMinor;

    @Column(nullable = false)
    private String currency;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "activated_at", nullable = false)
    private Instant activatedAt;
}

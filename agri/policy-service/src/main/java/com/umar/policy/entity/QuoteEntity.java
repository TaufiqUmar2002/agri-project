package com.umar.policy.entity;

import com.umar.enums.policy.ScoreSource;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "quotes")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuoteEntity {
    @Id
    @Column(name = "quote_id", length = 40)
    private String quoteId;

    @Column(name = "farmer_id", nullable = false)
    private UUID farmerId;

    @Column(name = "plot_id", nullable = false, length = 40)
    private String plotId;

    @Column(name = "product_id", nullable = false, length = 40)
    private String productId;

    @Column(name = "product_version", nullable = false)
    private Integer productVersion;

    @Column(name = "inputs_hash", nullable = false, length = 64)
    private String inputsHash;

    @Column(name = "sum_insured_minor", nullable = false)
    private Long sumInsuredMinor;

    @Column(name = "currency", nullable = false, length = 8)
    private String currency;

    @Column(name = "total_payable_minor", nullable = false)
    private Long totalPayableMinor;

    @Column(name = "subsidy_minor", nullable = false)
    private Long subsidyMinor;

    @Column(name = "subsidy_scheme_code", length = 40)
    private String subsidySchemeCode;

    @Column(name = "price_breakdown_json", nullable = false, columnDefinition = "TEXT")
    private String priceBreakdownJson;

    @Column(name = "factors_json", nullable = false, columnDefinition = "TEXT")
    private String factorsJson;

    @Column(name = "payout_tiers_json", nullable = false, columnDefinition = "TEXT")
    private String payoutTiersJson;

    @Column(name = "score_band", nullable = false, length = 24)
    private String scoreBand;

    @Enumerated(EnumType.STRING)
    @Column(name = "score_source", nullable = false, length = 24)
    private ScoreSource scoreSource;

    @Column(name = "language", nullable = false, length = 8)
    private String language;

    @Column(name = "channel", nullable = false, length = 16)
    private String channel;

    @Column(name = "waiting_period_days", nullable = false)
    private Integer waitingPeriodDays;

    @Column(name = "valid_until", nullable = false)
    private Instant validUntil;

    @Column(name = "pay_by", nullable = false)
    private Instant payBy;

    @Column(name = "used_by_policy_id", length = 40)
    private String usedByPolicyId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}

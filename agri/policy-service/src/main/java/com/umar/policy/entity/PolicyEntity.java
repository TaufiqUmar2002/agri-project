package com.umar.policy.entity;

import com.umar.enums.policy.*;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "policies", indexes = {
        @Index(name = "idx_policy_farmer", columnList = "farmer_id"),
        @Index(name = "idx_policy_plot", columnList = "plot_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PolicyEntity {
    @Id
    @Column(name = "policy_id", length = 40)
    private String policyId;

    @Column(name = "policy_number", nullable = false, unique = true, length = 40)
    private String policyNumber;

    @Column(name = "farmer_id", nullable = false)
    private UUID farmerId;

    @Column(name = "plot_id", nullable = false, length = 40)
    private String plotId;

    @Column(name = "quote_id", nullable = false, unique = true, length = 40)
    private String quoteId;

    @Column(name = "product_id", nullable = false, length = 40)
    private String productId;

    @Column(name = "product_version", nullable = false)
    private Integer productVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "peril", nullable = false, length = 32)
    private PerilType peril;

    @Column(name = "definition_hash", nullable = false, length = 64)
    private String definitionHash;

    @Column(name = "terms_snapshot_json", nullable = false, columnDefinition = "TEXT")
    private String termsSnapshotJson;

    @Column(name = "terms_hash", nullable = false, length = 64)
    private String termsHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private PolicyStatus status;

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

    @Column(name = "levy_minor", nullable = false)
    private Long levyMinor;

    @Enumerated(EnumType.STRING)
    @Column(name = "payer_type", nullable = false, length = 16)
    private PayerType payerType;

    @Column(name = "premium_status", nullable = false, length = 16)
    private String premiumStatus; // UNPAID | PAID | REFUNDED

    @Column(name = "pay_by")
    private Instant payBy;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "waiting_period_days", nullable = false)
    private Integer waitingPeriodDays;

    @Column(name = "waiting_ends_at")
    private Instant waitingEndsAt;

    @Column(name = "coverage_start")
    private Instant coverageStart;

    @Column(name = "coverage_end")
    private Instant coverageEnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "payout_state", nullable = false, length = 24)
    private PayoutState payoutState;

    @Column(name = "paid_to_date_minor", nullable = false)
    @Builder.Default
    private Long paidToDateMinor = 0L;

    @Column(name = "hold_reasons_csv", columnDefinition = "TEXT")
    private String holdReasonsCsv;

    @Column(name = "summary_text", nullable = false, columnDefinition = "TEXT")
    private String summaryText;

    @Column(name = "summary_language", nullable = false, length = 8)
    private String summaryLanguage;

    @Column(name = "terms_version", nullable = false, length = 16)
    private String termsVersion;

    @Column(name = "summary_doc_ref", length = 120)
    private String summaryDocRef;

    @Column(name = "acceptance_json", nullable = false, columnDefinition = "TEXT")
    private String acceptanceJson;

    @Column(name = "attributes_json", columnDefinition = "TEXT")
    private String attributesJson;

    @Column(name = "region_code", length = 40)
    private String regionCode;

    @Column(name = "crop_code", length = 40)
    private String cropCode;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public List<String> getHoldReasonList() {
        if (holdReasonsCsv == null || holdReasonsCsv.isBlank()) return List.of();
        return Arrays.stream(holdReasonsCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    public void setHoldReasons(Collection<PolicyHoldReason> reasons) {
        if (reasons == null || reasons.isEmpty()) {
            this.holdReasonsCsv = "";
        } else {
            this.holdReasonsCsv = String.join(",", reasons.stream().map(Enum::name).sorted().toList());
        }
    }
}

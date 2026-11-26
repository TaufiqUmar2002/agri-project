package com.umar.trigger.domain;

import com.umar.enums.trigger.DataSourceQuality;
import com.umar.enums.trigger.TriggerDecisionOutcome;
import com.umar.enums.trigger.TriggerDecisionStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "trigger_decisions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TriggerDecisionEntity {
    @Id
    @Column(name = "decision_id")
    private String decisionId;

    @Column(name = "evaluation_id", nullable = false)
    private String evaluationId;

    @Column(name = "policy_id", nullable = false)
    private String policyId;

    @Column(name = "farmer_id", nullable = false)
    private String farmerId;

    @Column(name = "plot_id", nullable = false)
    private String plotId;

    @Column(name = "region_code", nullable = false)
    private String regionCode;

    @Column(name = "rule_id", nullable = false)
    private String ruleId;

    @Column(name = "rule_version", nullable = false)
    private Integer ruleVersion;

    @Column(name = "rule_hash", nullable = false)
    private String ruleHash;

    @Column(name = "window_id", nullable = false)
    private String windowId;

    @Column(name = "window_start", nullable = false)
    private LocalDate windowStart;

    @Column(name = "window_end", nullable = false)
    private LocalDate windowEnd;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TriggerDecisionOutcome outcome;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TriggerDecisionStatus status;

    @Column(name = "tier_id")
    private String tierId;

    @Column(name = "payout_percent", nullable = false)
    private double payoutPercent;

    @Column(name = "indicated_payout_minor", nullable = false)
    private long indicatedPayoutMinor;

    @Column(nullable = false)
    private String currency;

    @Column(name = "computed_index_value", nullable = false)
    private double computedIndexValue;

    @Column(name = "threshold_value", nullable = false)
    private double thresholdValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_quality", nullable = false)
    private DataSourceQuality dataQuality;

    @Column(name = "evidence_hash", nullable = false)
    private String evidenceHash;

    @Column(name = "evidence_bundle_token", nullable = false)
    private String evidenceBundleToken;

    @Column(name = "evidence_json", nullable = false, columnDefinition = "TEXT")
    private String evidenceJson;

    @Column(name = "phases_json", nullable = false, columnDefinition = "TEXT")
    private String phasesJson;

    @Column(name = "corroboration_json", nullable = false, columnDefinition = "TEXT")
    private String corroborationJson;

    @Column(name = "history_json", nullable = false, columnDefinition = "TEXT")
    private String historyJson;

    @Column(name = "large_event", nullable = false)
    private boolean largeEvent;

    @Column(name = "first_reviewer_id")
    private String firstReviewerId;

    @Column(name = "decided_at", nullable = false)
    private Instant decidedAt;

    @Column(name = "version_num", nullable = false)
    private int versionNum;
}

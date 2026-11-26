package com.umar.trigger.domain;

import com.umar.enums.policy.PerilType;
import com.umar.enums.trigger.RuleStatus;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "trigger_rules")
@IdClass(TriggerRuleEntity.RuleVersionId.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TriggerRuleEntity {

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class RuleVersionId implements Serializable {
        private String ruleId;
        private Integer versionNum;
    }

    @Id
    @Column(name = "rule_id")
    private String ruleId;

    @Id
    @Column(name = "version_num")
    private Integer versionNum;

    @Column(name = "rule_code", nullable = false)
    private String ruleCode;

    @Column(name = "product_id", nullable = false)
    private String productId;

    @Column(name = "product_version", nullable = false)
    private Integer productVersion;

    @Column(name = "contractual_definition_hash", nullable = false)
    private String contractualDefinitionHash;

    @Column(name = "definition_hash", nullable = false)
    private String definitionHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PerilType peril;

    @Column(name = "region_codes_csv", nullable = false, columnDefinition = "TEXT")
    private String regionCodesCsv;

    @Column(name = "index_type", nullable = false)
    private String indexType;

    @Column(name = "observation_window_json", columnDefinition = "TEXT")
    private String observationWindowJson;

    @Column(name = "data_sources_json", columnDefinition = "TEXT")
    private String dataSourcesJson;

    @Column(name = "corroboration_json", columnDefinition = "TEXT")
    private String corroborationJson;

    @Column(name = "phases_json", columnDefinition = "TEXT")
    private String phasesJson;

    @Column(name = "tiers_json", nullable = false, columnDefinition = "TEXT")
    private String tiersJson;

    @Column(name = "quality_gates_json", columnDefinition = "TEXT")
    private String qualityGatesJson;

    @Column(name = "cooling_period_json", columnDefinition = "TEXT")
    private String coolingPeriodJson;

    @Column(name = "grace_period_hours", nullable = false)
    private Integer gracePeriodHours;

    @Column(name = "backtest_ref")
    private String backtestRef;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RuleStatus status;

    @Column(name = "author_id", nullable = false)
    private String authorId;

    @Column(name = "approvals_json", nullable = false, columnDefinition = "TEXT")
    private String approvalsJson;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "activated_at")
    private Instant activatedAt;
}

package com.umar.trigger.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "trigger_simulation_runs")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SimulationRunEntity {
    @Id
    @Column(name = "simulation_id")
    private String simulationId;

    @Column(name = "rule_id")
    private String ruleId;

    @Column(name = "rule_version")
    private Integer ruleVersion;

    @Column(name = "definition_hash", nullable = false)
    private String definitionHash;

    @Column(name = "region_codes_csv", nullable = false, columnDefinition = "TEXT")
    private String regionCodesCsv;

    @Column(name = "from_date", nullable = false)
    private LocalDate fromDate;

    @Column(name = "to_date", nullable = false)
    private LocalDate toDate;

    @Column(name = "seasons_covered", nullable = false)
    private int seasonsCovered;

    @Column(name = "expected_loss_ratio", nullable = false)
    private double expectedLossRatio;

    @Column(name = "burning_cost_percent", nullable = false)
    private double burningCostPercent;

    @Column(name = "data_coverage_percent", nullable = false)
    private double dataCoveragePercent;

    @Column(name = "summary_json", nullable = false, columnDefinition = "TEXT")
    private String summaryJson;

    @Column(name = "per_season_json", nullable = false, columnDefinition = "TEXT")
    private String perSeasonJson;

    @Column(name = "comparison_json", columnDefinition = "TEXT")
    private String comparisonJson;

    @Column(name = "sensitivity_json", columnDefinition = "TEXT")
    private String sensitivityJson;

    @Column(name = "report_token")
    private String reportToken;

    @Column(name = "valid_until", nullable = false)
    private LocalDate validUntil;

    @Column(name = "requested_by", nullable = false)
    private String requestedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}

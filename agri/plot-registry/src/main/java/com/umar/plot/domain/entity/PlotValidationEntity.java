package com.umar.plot.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "plot_validations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlotValidationEntity {

    @Id
    @Column(name = "validation_id", nullable = false)
    private UUID validationId;

    @Column(name = "plot_id", nullable = false, length = 64)
    private String plotId;

    @Column(name = "geometry_version", nullable = false)
    private int geometryVersion;

    @Column(name = "rule_set_version", nullable = false, length = 32)
    private String ruleSetVersion;

    @Column(name = "outcome", nullable = false, length = 24)
    private String outcome;

    @Column(name = "reason_codes_csv", length = 512)
    private String reasonCodesCsv;

    @Column(name = "metrics_json", nullable = false, columnDefinition = "TEXT")
    private String metricsJson;

    @Column(name = "dry_run", nullable = false)
    private boolean dryRun;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}

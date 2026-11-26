package com.umar.trigger.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "trigger_observations")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ObservationArchiveEntity {
    @Id
    @Column(name = "ingest_id")
    private String ingestId;

    @Column(name = "source_id", nullable = false)
    private String sourceId;

    @Column(name = "region_code", nullable = false)
    private String regionCode;

    @Column(name = "plot_id")
    private String plotId;

    @Column(nullable = false)
    private String channel;

    @Column(name = "metric_value", nullable = false)
    private double metricValue;

    @Column(name = "observed_date", nullable = false)
    private LocalDate observedDate;

    @Column(name = "quality_flags_csv", columnDefinition = "TEXT")
    private String qualityFlagsCsv;

    @Column(nullable = false)
    private int revision;

    @Column(name = "ingested_at", nullable = false)
    private Instant ingestedAt;
}

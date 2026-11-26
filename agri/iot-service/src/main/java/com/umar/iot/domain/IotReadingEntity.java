package com.umar.iot.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity @Table(name = "iot_readings")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class IotReadingEntity {
    @Id @Column(name = "ingest_id") private String ingestId;
    @Column(name = "device_id", nullable = false) private String deviceId;
    @Column(name = "plot_id") private String plotId;
    @Column(name = "district_code") private String districtCode;
    @Column(nullable = false) private String channel;
    @Column(name = "raw_value", nullable = false) private double rawValue;
    @Column(name = "metric_value", nullable = false) private double metricValue;
    @Column(nullable = false) private String unit;
    @Column(name = "observed_at", nullable = false) private Instant observedAt;
    @Column(name = "ingested_at", nullable = false) private Instant ingestedAt;
    @Column(name = "quality_flags_csv", nullable = false, columnDefinition = "TEXT") private String qualityFlagsCsv;
    @Column(name = "calibration_version", nullable = false) private int calibrationVersion;
    @Column(nullable = false) private int revision;
    @Column(name = "late_arrival", nullable = false) private boolean lateArrival;
}

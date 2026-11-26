package com.umar.plot.domain.entity;

import com.umar.enums.plot.*;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Entity
@Table(name = "plots")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlotEntity {

    @Id
    @Column(name = "plot_id", nullable = false, length = 64)
    private String plotId;

    @Column(name = "farmer_id", nullable = false)
    private UUID farmerId;

    @Column(name = "name", length = 60)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private PlotStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "capture_method", nullable = false, length = 24)
    private CaptureMethod captureMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "accuracy_class", nullable = false, length = 16)
    private AccuracyClass accuracyClass;

    @Convert(converter = com.umar.crypto.VaultEncryptedStringConverter.class)
    @Column(name = "geometry_geojson", nullable = false, columnDefinition = "TEXT")
    private String geometryGeojson;

    @Column(name = "geometry_version", nullable = false)
    private int geometryVersion;

    @Column(name = "geometry_hash", nullable = false, length = 128)
    private String geometryHash;

    @Column(name = "area_ha", nullable = false)
    private double areaHa;

    @Column(name = "min_width_m", nullable = false)
    private double minWidthM;

    @Column(name = "centroid_lat", nullable = false)
    private double centroidLat;

    @Column(name = "centroid_lon", nullable = false)
    private double centroidLon;

    @Column(name = "bbox", nullable = false, length = 128)
    private String bbox;

    @Column(name = "geohash5", nullable = false, length = 10)
    private String geohash5;

    @Column(name = "geohash7", nullable = false, length = 12)
    private String geohash7;

    @Column(name = "country", nullable = false, length = 8)
    private String country;

    @Column(name = "district", nullable = false, length = 64)
    private String district;

    @Column(name = "ward", nullable = false, length = 64)
    private String ward;

    @Column(name = "flags_csv", length = 512)
    private String flagsCsv;

    @Enumerated(EnumType.STRING)
    @Column(name = "coverage_status", nullable = false, length = 24)
    private CoverageStatus coverageStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "monitoring_status", nullable = false, length = 24)
    private MonitoringStatus monitoringStatus;

    @Column(name = "evidence_tokens_csv", length = 1024)
    private String evidenceTokensCsv;

    @Column(name = "registered_by")
    private UUID registeredBy;

    @Column(name = "delegation_id")
    private UUID delegationId;

    @Column(name = "device_id", length = 128)
    private String deviceId;

    @Column(name = "attributes_json", columnDefinition = "TEXT")
    private String attributesJson;

    @Column(name = "reviewer_id", length = 64)
    private String reviewerId;

    @Column(name = "second_reviewer_id", length = 64)
    private String secondReviewerId;

    @Column(name = "second_review_pending", nullable = false)
    private boolean secondReviewPending;

    @Column(name = "reviewer_note", length = 1000)
    private String reviewerNote;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "validated_at")
    private Instant validatedAt;

    public Set<PlotFlag> getFlags() {
        if (flagsCsv == null || flagsCsv.isBlank()) return EnumSet.noneOf(PlotFlag.class);
        return Arrays.stream(flagsCsv.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                .map(PlotFlag::valueOf).collect(Collectors.toCollection(() -> EnumSet.noneOf(PlotFlag.class)));
    }

    public void setFlags(Collection<PlotFlag> flags) {
        this.flagsCsv = (flags == null || flags.isEmpty())
                ? null : flags.stream().map(Enum::name).sorted().collect(Collectors.joining(","));
    }
}

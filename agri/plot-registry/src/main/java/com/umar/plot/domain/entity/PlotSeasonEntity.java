package com.umar.plot.domain.entity;

import com.umar.enums.plot.IrrigationType;
import com.umar.enums.plot.SeasonStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "plot_seasons")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlotSeasonEntity {

    @Id
    @Column(name = "season_id", nullable = false, length = 64)
    private String seasonId;

    @Column(name = "plot_id", nullable = false, length = 64)
    private String plotId;

    @Column(name = "season_label", nullable = false, length = 32)
    private String seasonLabel;

    @Column(name = "crop_code", nullable = false, length = 32)
    private String cropCode;

    @Column(name = "planting_date", nullable = false)
    private LocalDate plantingDate;

    @Column(name = "expected_harvest_date", nullable = false)
    private LocalDate expectedHarvestDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "irrigation_type", nullable = false, length = 24)
    private IrrigationType irrigationType;

    @Column(name = "seed_variety", length = 60)
    private String seedVariety;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private SeasonStatus status;

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}

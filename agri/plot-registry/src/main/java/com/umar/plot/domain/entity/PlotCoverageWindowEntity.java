package com.umar.plot.domain.entity;

import com.umar.enums.plot.CoverageStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "plot_coverage_windows")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlotCoverageWindowEntity {

    @Id
    @Column(name = "window_id", nullable = false)
    private UUID windowId;

    @Column(name = "plot_id", nullable = false, length = 64)
    private String plotId;

    @Column(name = "policy_id", nullable = false, length = 64)
    private String policyId;

    @Column(name = "valid_from", nullable = false)
    private Instant validFrom;

    @Column(name = "valid_to", nullable = false)
    private Instant validTo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private CoverageStatus status;
}

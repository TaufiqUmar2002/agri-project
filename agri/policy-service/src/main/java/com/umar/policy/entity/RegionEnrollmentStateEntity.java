package com.umar.policy.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "region_enrollment_state")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegionEnrollmentStateEntity {
    @Id
    @Column(name = "region_code", length = 40)
    private String regionCode;

    @Column(name = "status", nullable = false, length = 16)
    private String status; // OPEN | CLOSED

    @Column(name = "reason")
    private String reason;

    @Column(name = "source_forecast_id", length = 64)
    private String sourceForecastId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}

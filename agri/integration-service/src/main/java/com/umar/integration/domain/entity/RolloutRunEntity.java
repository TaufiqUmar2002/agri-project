package com.umar.integration.domain.entity;

import com.umar.enums.integration.RolloutStage;
import com.umar.integration.domain.enums.RolloutStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "rollout_runs", indexes = {
        @Index(name = "idx_rollout_ext_status", columnList = "extensionId,status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RolloutRunEntity {

    @Id
    @Column(length = 64)
    private String rolloutId;

    @Column(nullable = false, length = 64)
    private String extensionId;

    @Column(nullable = false, length = 32)
    private String extensionVersion;

    @Column(nullable = false, length = 128)
    private String hookId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private RolloutStage stage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private RolloutStatus status;

    @Lob
    @Column(nullable = false)
    private String scopeJson;

    @Lob
    private String canaryJson;

    private Integer durationHours;

    @Lob
    private String criteriaJson;

    @Column(nullable = false)
    private Long invocations;

    @Column(nullable = false)
    private Long errors;

    @Column(nullable = false)
    private Long timeouts;

    @Column(nullable = false)
    private Long p50Ms;

    @Column(nullable = false)
    private Long p95Ms;

    @Lob
    private String outcomeCountsJson;

    private Double agreementPercent;

    private Long unexpectedHolds;

    private Long wouldHaveRejected;

    @Lob
    private String sampleRefsJson;

    @Column(length = 32)
    private String verdict;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(nullable = false, length = 128)
    private String startedBy;

    private Instant endsAt;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;
}

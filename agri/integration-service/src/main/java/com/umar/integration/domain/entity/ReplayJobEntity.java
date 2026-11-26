package com.umar.integration.domain.entity;

import com.umar.integration.domain.enums.JobStatus;
import com.umar.integration.domain.enums.ReplayMode;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "replay_jobs", indexes = {
        @Index(name = "idx_replay_sub_created", columnList = "subscriptionId,createdAt")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReplayJobEntity {

    @Id
    @Column(length = 64)
    private String jobId;

    @Column(nullable = false, length = 64)
    private String subscriptionId;

    @Column(nullable = false, length = 64)
    private String partnerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ReplayMode mode;

    @Column(length = 64)
    private String targetDeliveryId;

    private Instant fromTime;

    private Instant toTime;

    private Integer fromPartition;

    private Long fromOffset;

    private Long toOffset;

    @Lob
    private String eventTypesJson;

    @Column(nullable = false)
    private boolean includeDelivered;

    @Column(nullable = false, length = 300)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private JobStatus status;

    @Column(nullable = false)
    private Integer estimatedEvents;

    @Column(nullable = false)
    private Integer estimatedDurationSeconds;

    @Column(nullable = false)
    private Integer deliveredCount;

    @Column(nullable = false)
    private Integer failedCount;

    @Column(nullable = false)
    private Integer skippedCount;

    @Column(nullable = false, length = 128)
    private String requestedBy;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant completedAt;
}

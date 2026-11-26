package com.umar.audit.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "telemetry_batches")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class TelemetryBatchEntity {
    @Id
    private UUID id;
    @Column(name = "batch_id", nullable = false, unique = true)
    private String batchId;
    @Column(name = "device_id", nullable = false)
    private String deviceId;
    @Column(name = "window_start", nullable = false)
    private Instant windowStart;
    @Column(name = "window_end", nullable = false)
    private Instant windowEnd;
    @Column(name = "reading_count", nullable = false)
    private long readingCount;
    @Column(name = "merkle_root")
    private String merkleRoot;
    @Column(name = "leaves_json", nullable = false, columnDefinition = "TEXT")
    private String leavesJson;
    @Column(nullable = false)
    private boolean sealed;
    @Column(name = "checkpoint_id")
    private String checkpointId;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}

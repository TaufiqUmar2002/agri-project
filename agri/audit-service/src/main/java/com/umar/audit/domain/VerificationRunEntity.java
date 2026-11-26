package com.umar.audit.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "verification_runs")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class VerificationRunEntity {
    @Id
    private UUID id;
    @Column(name = "run_id", nullable = false, unique = true)
    private String runId;
    @Column(nullable = false)
    private String scope;
    @Column(name = "chain_id")
    private String chainId;
    @Column(name = "claim_id")
    private String claimId;
    @Column(nullable = false)
    private String mode;
    @Column(nullable = false)
    private String status;
    @Column(name = "checked_records", nullable = false)
    private long checkedRecords;
    @Column(name = "mismatch_count", nullable = false)
    private long mismatchCount;
    @Column(name = "details_json", columnDefinition = "TEXT")
    private String detailsJson;
    @Column(name = "initiated_by", nullable = false)
    private String initiatedBy;
    @Column(nullable = false)
    private String reason;
    @Column(nullable = false)
    private String note;
    @Column(name = "started_at", nullable = false)
    private Instant startedAt;
    @Column(name = "completed_at", nullable = false)
    private Instant completedAt;
}

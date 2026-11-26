package com.umar.audit.domain;

import com.umar.enums.audit.ReportType;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "regulatory_reports")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class RegulatoryReportEntity {
    @Id
    private UUID id;
    @Column(name = "report_id", nullable = false, unique = true)
    private String reportId;
    @Enumerated(EnumType.STRING)
    @Column(name = "report_type", nullable = false)
    private ReportType reportType;
    @Column(nullable = false)
    private String format;
    @Column(nullable = false)
    private String status;
    @Column(name = "schedule_id")
    private String scheduleId;
    @Column(name = "period_from")
    private String periodFrom;
    @Column(name = "period_to")
    private String periodTo;
    @Column(name = "filters_json", columnDefinition = "TEXT")
    private String filtersJson;
    @Column(name = "checkpoints_json", columnDefinition = "TEXT")
    private String checkpointsJson;
    @Column(name = "row_counts_json", columnDefinition = "TEXT")
    private String rowCountsJson;
    @Column(name = "totals_json", columnDefinition = "TEXT")
    private String totalsJson;
    @Column(name = "report_hash", nullable = false)
    private String reportHash;
    @Column(name = "created_by")
    private String createdBy;
    @Column(name = "approved_by")
    private String approvedBy;
    @Column(name = "next_run_at")
    private Instant nextRunAt;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}

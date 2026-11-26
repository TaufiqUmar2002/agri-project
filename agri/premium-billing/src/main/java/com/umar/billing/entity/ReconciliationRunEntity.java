package com.umar.billing.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "reconciliation_runs")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ReconciliationRunEntity {
    @Id
    @Column(name = "run_id", length = 48)
    private String runId;
    @Column(name = "source", nullable = false, length = 32)
    private String source;
    @Column(name = "statement_date", nullable = false)
    private LocalDate statementDate;
    @Column(name = "checksum", length = 64)
    private String checksum;
    @Column(name = "total_lines", nullable = false)
    private int totalLines;
    @Column(name = "matched_lines", nullable = false)
    private int matchedLines;
    @Column(name = "unmatched_lines", nullable = false)
    private int unmatchedLines;
    @Column(name = "summary_json", nullable = false, columnDefinition = "TEXT")
    private String summaryJson;
    @Column(name = "completed_at", nullable = false)
    private Instant completedAt;
}

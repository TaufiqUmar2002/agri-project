package com.umar.payout.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "payout_statement_imports")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PayoutStatementImportEntity {
    @Id
    @Column(name = "import_id")
    private String importId;

    @Column(nullable = false)
    private String connection;

    @Column(name = "statement_date", nullable = false)
    private LocalDate statementDate;

    @Column(nullable = false)
    private String checksum;

    @Column(name = "total_lines", nullable = false)
    private int totalLines;

    @Column(name = "matched_lines", nullable = false)
    private int matchedLines;

    @Column(name = "unmatched_lines", nullable = false)
    private int unmatchedLines;

    @Column(name = "total_minor", nullable = false)
    private long totalMinor;

    @Column(name = "fees_minor", nullable = false)
    private long feesMinor;

    @Column(name = "breaks_count", nullable = false)
    private int breaksCount;

    @Column(name = "imported_at", nullable = false)
    private Instant importedAt;
}

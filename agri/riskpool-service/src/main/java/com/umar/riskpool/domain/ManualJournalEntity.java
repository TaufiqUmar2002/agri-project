package com.umar.riskpool.domain;

import com.umar.enums.riskpool.JournalType;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

@Entity @Table(name = "pool_manual_journals")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ManualJournalEntity {
    @Id @Column(name = "journal_id") private String journalId;
    @Column(name = "pool_id", nullable = false) private String poolId;
    @Enumerated(EnumType.STRING) @Column(name = "journal_type", nullable = false) private JournalType journalType;
    @Column(name = "value_date", nullable = false) private LocalDate valueDate;
    @Column(nullable = false) private String currency;
    @Column(name = "total_minor", nullable = false) private long totalMinor;
    @Column(name = "lines_json", nullable = false, columnDefinition = "TEXT") private String linesJson;
    private String reverses;
    @Column(name = "reason_code", nullable = false) private String reasonCode;
    @Column(name = "evidence_tokens_csv", nullable = false, columnDefinition = "TEXT") private String evidenceTokensCsv;
    @Column(nullable = false, columnDefinition = "TEXT") private String note;
    @Column(nullable = false) private String status;
    @Column(name = "approval_id") private String approvalId;
    @Column(name = "maker_id", nullable = false) private String makerId;
    @Column(name = "posted_tx_id") private String postedTxId;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
}

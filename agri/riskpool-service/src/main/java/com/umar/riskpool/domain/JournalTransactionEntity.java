package com.umar.riskpool.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

@Entity @Table(name = "pool_journal_transactions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class JournalTransactionEntity {
    @Id @Column(name = "transaction_id") private String transactionId;
    @Column(name = "pool_id", nullable = false) private String poolId;
    @Column(name = "source_type", nullable = false) private String sourceType;
    @Column(name = "source_event_id", nullable = false) private String sourceEventId;
    @Column(name = "journal_type") private String journalType;
    @Column(name = "value_date", nullable = false) private LocalDate valueDate;
    @Column(name = "posting_date", nullable = false) private LocalDate postingDate;
    @Column(name = "prior_period", nullable = false) private boolean priorPeriod;
    @Column(name = "total_minor", nullable = false) private long totalMinor;
    @Column(nullable = false) private String currency;
    @Column(name = "lines_json", nullable = false, columnDefinition = "TEXT") private String linesJson;
    @Column(name = "dimensions_json", columnDefinition = "TEXT") private String dimensionsJson;
    @Column(name = "reverses_tx_id") private String reversesTxId;
    @Column(name = "reversed_by_tx_id") private String reversedByTxId;
    @Column(name = "suspense_reason") private String suspenseReason;
    @Column(name = "prev_hash", nullable = false) private String prevHash;
    @Column(name = "entry_hash", nullable = false) private String entryHash;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
}

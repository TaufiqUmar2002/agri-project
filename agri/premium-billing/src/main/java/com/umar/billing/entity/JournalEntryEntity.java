package com.umar.billing.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "journal_entries")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class JournalEntryEntity {
    @Id
    @Column(name = "entry_id")
    private UUID entryId;
    @Column(name = "account", nullable = false, length = 40)
    private String account;
    @Column(name = "debit_minor", nullable = false)
    private Long debitMinor;
    @Column(name = "credit_minor", nullable = false)
    private Long creditMinor;
    @Column(name = "currency", nullable = false, length = 8)
    private String currency;
    @Column(name = "reference", nullable = false, length = 64)
    private String reference;
    @Column(name = "posted_at", nullable = false)
    private Instant postedAt;
}

package com.umar.entity.dataProtection;

import jakarta.persistence.*;
import lombok.Data;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "BLIND_INDEX_ENTRY")
@EntityListeners(AuditingEntityListener.class)
@Data
public class BlindIndexEntry {

    @Id
    private UUID id;

    private UUID tokenId;

    @Column(name = "DATA_CLASS")
    private String dataClass;

    @Column(name = "SUBJECT_REF")
    private String subjectRef;

    @Column(name = "INDEX_KEY_VERSION_ID")
    private UUID indexKeyVersionId;

    @Column(name = "INDEX_HASH", columnDefinition = "BYTEA")
    private byte[] indexHash;

    @Column(name = "CREATED_AT")
    private Instant createdAt;

    public BlindIndexEntry() {
        this.id = UUID.randomUUID();
        this.createdAt = Instant.now();
    }
}
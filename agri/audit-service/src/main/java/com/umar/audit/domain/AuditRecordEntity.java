package com.umar.audit.domain;

import com.umar.enums.audit.RetentionClass;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_records")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class AuditRecordEntity {
    @Id
    private UUID id;
    @Column(name = "record_id", nullable = false, unique = true)
    private String recordId;
    @Column(name = "chain_id", nullable = false)
    private String chainId;
    @Column(nullable = false)
    private long seq;
    @Enumerated(EnumType.STRING)
    @Column(name = "retention_class", nullable = false)
    private RetentionClass retentionClass;
    @Column(name = "event_id", nullable = false)
    private String eventId;
    @Column(name = "event_type", nullable = false)
    private String eventType;
    @Column(name = "producer_service", nullable = false)
    private String producerService;
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;
    @Column(name = "ingested_at", nullable = false)
    private Instant ingestedAt;
    @Column(name = "subject_type")
    private String subjectType;
    @Column(name = "subject_id")
    private String subjectId;
    @Column(name = "claim_id")
    private String claimId;
    @Column(name = "policy_id")
    private String policyId;
    @Column(name = "plot_id")
    private String plotId;
    @Column(name = "actor_id")
    private String actorId;
    @Convert(converter = com.umar.crypto.VaultEncryptedStringConverter.class)
    @Column(name = "payload_json", nullable = false, columnDefinition = "TEXT")
    private String payloadJson;
    @Column(name = "flags_json", columnDefinition = "TEXT")
    private String flagsJson;
    @Column(nullable = false)
    private boolean redacted;
    @Column(name = "prev_hash", nullable = false)
    private String prevHash;
    @Column(name = "record_hash", nullable = false)
    private String recordHash;
    @Column(name = "checkpoint_id")
    private String checkpointId;
}

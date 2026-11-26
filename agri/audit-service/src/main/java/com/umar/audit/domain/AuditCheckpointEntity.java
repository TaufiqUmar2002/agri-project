package com.umar.audit.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_checkpoints")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class AuditCheckpointEntity {
    @Id
    private UUID id;
    @Column(name = "checkpoint_id", nullable = false, unique = true)
    private String checkpointId;
    @Column(name = "chain_id", nullable = false)
    private String chainId;
    @Column(name = "from_seq", nullable = false)
    private long fromSeq;
    @Column(name = "to_seq", nullable = false)
    private long toSeq;
    @Column(name = "merkle_root", nullable = false)
    private String merkleRoot;
    @Column(name = "head_hash", nullable = false)
    private String headHash;
    @Column(nullable = false)
    private String signature;
    @Column(name = "key_id", nullable = false)
    private String keyId;
    @Column(name = "signed_at", nullable = false)
    private Instant signedAt;
}

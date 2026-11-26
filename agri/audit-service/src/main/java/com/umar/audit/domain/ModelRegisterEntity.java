package com.umar.audit.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "model_register")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ModelRegisterEntity {
    @Id
    private UUID id;
    @Column(name = "model_id", nullable = false)
    private String modelId;
    @Column(nullable = false)
    private String version;
    @Column(nullable = false)
    private String component;
    @Column(name = "approval_status", nullable = false)
    private String approvalStatus;
    @Column(name = "rule_hash", nullable = false)
    private String ruleHash;
    @Column(name = "approved_by")
    private String approvedBy;
    @Column(name = "governance_notes", columnDefinition = "TEXT")
    private String governanceNotes;
    @Column(name = "effective_from", nullable = false)
    private Instant effectiveFrom;
    @Column(name = "effective_to")
    private Instant effectiveTo;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}

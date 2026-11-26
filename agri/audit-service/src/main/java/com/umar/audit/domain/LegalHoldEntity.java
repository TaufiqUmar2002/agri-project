package com.umar.audit.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "legal_holds")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class LegalHoldEntity {
    @Id
    private UUID id;
    @Column(name = "hold_id", nullable = false, unique = true)
    private String holdId;
    @Column(nullable = false)
    private String status;
    @Column(name = "case_ref", nullable = false)
    private String caseRef;
    @Column(name = "reason_code", nullable = false)
    private String reasonCode;
    @Column(name = "scope_json", nullable = false, columnDefinition = "TEXT")
    private String scopeJson;
    @Column(name = "resolved_json", columnDefinition = "TEXT")
    private String resolvedJson;
    @Column(name = "placed_by", nullable = false)
    private String placedBy;
    @Column(name = "release_proposed_by")
    private String releaseProposedBy;
    @Column(name = "release_approved_by")
    private String releaseApprovedBy;
    @Column(name = "proposal_hash")
    private String proposalHash;
    @Column(nullable = false)
    private String note;
    @Version
    private int version;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}

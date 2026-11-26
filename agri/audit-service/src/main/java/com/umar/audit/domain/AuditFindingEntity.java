package com.umar.audit.domain;

import com.umar.enums.audit.FindingSeverity;
import com.umar.enums.audit.FindingStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "audit_findings")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class AuditFindingEntity {
    @Id
    private UUID id;
    @Column(name = "finding_id", nullable = false, unique = true)
    private String findingId;
    @Column(name = "rule_code", nullable = false)
    private String ruleCode;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FindingSeverity severity;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FindingStatus status;
    @Column(name = "subject_type")
    private String subjectType;
    @Column(name = "subject_id")
    private String subjectId;
    @Column(name = "claim_id")
    private String claimId;
    @Column(name = "producer_service")
    private String producerService;
    @Column(nullable = false)
    private String summary;
    @Column(name = "details_json", columnDefinition = "TEXT")
    private String detailsJson;
    @Column(name = "evidence_refs_json", columnDefinition = "TEXT")
    private String evidenceRefsJson;
    @Column(name = "pending_action")
    private String pendingAction;
    @Column(name = "proposal_hash")
    private String proposalHash;
    @Column(name = "proposed_by")
    private String proposedBy;
    @Column(name = "approved_by")
    private String approvedBy;
    @Column(name = "accept_until")
    private LocalDate acceptUntil;
    @Column(name = "history_json", columnDefinition = "TEXT")
    private String historyJson;
    @Version
    private int version;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}

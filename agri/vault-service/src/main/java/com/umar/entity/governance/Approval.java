package com.umar.entity.governance;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "APPROVAL")
@EntityListeners(AuditingEntityListener.class)
@AllArgsConstructor
@NoArgsConstructor
@Data
public class Approval {

    @Id
    private UUID id;

    private String type;

    private String subjectId;

    private String subjectHash;

    private String requiredRoles;

    @Column(name = "REQUESTED_BY")
    private String requestedBy;

    @Column(name = "INVESTIGATOR_REF")
    private String investigatorRef;

    @ElementCollection(fetch = FetchType.EAGER)
    private List<String> decisions;

    @Column(name = "STATUS")
    private String status = "PENDING"; // PENDING | APPROVED | REJECTED | EXPIRED

    @Column(name = "DECISION")
    private String decision;

    @Column(name = "DECIDED_BY")
    private String decidedBy;

    @Column(name = "DECIDED_AT")
    private Instant decidedAt;

    @Column(name = "NOTE", length = 1000)
    private String note;

    @Column(name = "CHECKLIST_JSON", columnDefinition = "TEXT")
    private String checklistJson;

    @Column(name = "REQUIRED_APPROVALS")
    private int requiredApprovals = 1;

    @Column(name = "APPROVAL_COUNT")
    private int approvalCount = 0;

    @Column(name = "CREATED_AT")
    private Instant createdAt = Instant.now();

    @Column(name = "EXPIRES_AT")
    private Instant expiresAt;
}

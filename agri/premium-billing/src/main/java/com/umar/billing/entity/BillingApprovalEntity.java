package com.umar.billing.entity;

import com.umar.enums.billing.ApprovalStatus;
import com.umar.enums.billing.ApprovalType;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "billing_approvals")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class BillingApprovalEntity {
    @Id
    @Column(name = "approval_id", length = 40)
    private String approvalId;
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 24)
    private ApprovalType type;
    @Column(name = "subject_id", nullable = false, length = 40)
    private String subjectId;
    @Column(name = "subject_hash", nullable = false, length = 64)
    private String subjectHash;
    @Column(name = "requested_by", nullable = false, length = 64)
    private String requestedBy;
    @Column(name = "required_levels", nullable = false)
    private int requiredLevels;
    @Column(name = "required_roles_csv", nullable = false, length = 120)
    private String requiredRolesCsv;
    @Column(name = "decisions_json", nullable = false, columnDefinition = "TEXT")
    private String decisionsJson;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private ApprovalStatus status;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}

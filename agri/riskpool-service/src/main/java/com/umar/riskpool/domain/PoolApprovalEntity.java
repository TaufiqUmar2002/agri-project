package com.umar.riskpool.domain;

import com.umar.enums.riskpool.RiskPoolApprovalType;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity @Table(name = "pool_approvals")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PoolApprovalEntity {
    @Id @Column(name = "approval_id") private String approvalId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private RiskPoolApprovalType type;
    @Column(name = "subject_id", nullable = false) private String subjectId;
    @Column(name = "subject_hash", nullable = false) private String subjectHash;
    @Column(name = "requested_by", nullable = false) private String requestedBy;
    @Column(name = "required_levels", nullable = false) private int requiredLevels;
    @Column(name = "required_roles_csv", nullable = false) private String requiredRolesCsv;
    @Column(name = "decisions_json", nullable = false, columnDefinition = "TEXT") private String decisionsJson;
    @Column(nullable = false) private String status;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
}

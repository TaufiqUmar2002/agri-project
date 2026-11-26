package com.umar.policy.entity;

import com.umar.enums.auth.AuthRole;
import com.umar.enums.policy.ProductDecisionType;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "product_approvals",
        uniqueConstraints = @UniqueConstraint(name = "uq_product_role_approval", columnNames = {"product_id", "version_num", "approver_role"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductApprovalEntity {
    @Id
    @Column(name = "approval_id")
    private UUID approvalId;

    @Column(name = "product_id", nullable = false, length = 40)
    private String productId;

    @Column(name = "version_num", nullable = false)
    private Integer versionNum;

    @Column(name = "approver_id", nullable = false, length = 64)
    private String approverId;

    @Enumerated(EnumType.STRING)
    @Column(name = "approver_role", nullable = false, length = 32)
    private AuthRole approverRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision", nullable = false, length = 24)
    private ProductDecisionType decision;

    @Column(name = "definition_hash", nullable = false, length = 64)
    private String definitionHash;

    @Column(name = "note", nullable = false, columnDefinition = "TEXT")
    private String note;

    @Column(name = "decided_at", nullable = false)
    private Instant decidedAt;
}

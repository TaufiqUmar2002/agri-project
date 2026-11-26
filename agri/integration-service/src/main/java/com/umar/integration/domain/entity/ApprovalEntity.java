package com.umar.integration.domain.entity;

import com.umar.integration.domain.enums.ApprovalStatus;
import com.umar.integration.domain.enums.ApprovalType;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "approvals", indexes = {
        @Index(name = "idx_approval_subject", columnList = "subjectId,status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApprovalEntity {

    @Id
    @Column(length = 64)
    private String approvalId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ApprovalType type;

    @Column(nullable = false, length = 64)
    private String subjectId;

    @Column(nullable = false)
    private Integer subjectVersion;

    @Column(nullable = false, length = 128)
    private String subjectHash;

    @Lob
    @Column(nullable = false)
    private String requiredRolesJson;

    @Lob
    @Column(nullable = false)
    private String decisionsJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ApprovalStatus status;

    @Column(nullable = false, length = 128)
    private String requestedBy;

    @Column(nullable = false)
    private Instant slaDueAt;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;
}

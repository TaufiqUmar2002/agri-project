package com.umar.entity.keyManagement;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "LEGAL_HOLD")
@EntityListeners(AuditingEntityListener.class)
@AllArgsConstructor
@NoArgsConstructor
@Data
public class LegalHold {

    @Id
    private UUID id;

    @Column(name = "CASE_REF")
    private String caseRef;

    @Column(name = "CASE_REF_HASH")
    private String caseRefHash;

    @Column(name = "AUTHORITY")
    private String authority;

    @Column(name = "SCOPE", columnDefinition = "TEXT")
    private String scope;

    @Column(name = "STATUS")
    private String status; // ACTIVE | LIFT_PENDING_APPROVAL | LIFTED | EXPIRED

    @Column(name = "REASON", length = 1000)
    private String reason;

    @Column(name = "REVIEW_DATE")
    private String reviewDate;

    @Column(name = "APPROVAL_ID")
    private UUID approvalId;

    @Column(name = "CREATED_AT")
    private Instant createdAt = Instant.now();

    @Column(name = "LIFTED_AT")
    private Instant liftedAt;
}

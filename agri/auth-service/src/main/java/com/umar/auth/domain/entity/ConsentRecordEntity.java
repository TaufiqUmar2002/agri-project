package com.umar.auth.domain.entity;

import com.umar.enums.auth.ConsentEvidenceType;
import com.umar.enums.auth.ConsentPurpose;
import com.umar.enums.auth.ConsentStatus;
import com.umar.enums.auth.RegistrationChannel;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "consent_records")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsentRecordEntity {

    @Id
    @Column(name = "consent_id", nullable = false, updatable = false)
    private UUID consentId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, updatable = false, length = 40)
    private ConsentPurpose purpose;

    @Column(name = "text_version", nullable = false, updatable = false, length = 32)
    private String textVersion;

    @Column(name = "language", nullable = false, updatable = false, length = 8)
    private String language;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, updatable = false, length = 20)
    private RegistrationChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "evidence_type", nullable = false, updatable = false, length = 32)
    private ConsentEvidenceType evidenceType;

    @Column(name = "evidence_ref", updatable = false, length = 128)
    private String evidenceRef;

    @Column(name = "partner_id", updatable = false, length = 64)
    private String partnerId;

    @Column(name = "captured_by", nullable = false, updatable = false)
    private UUID capturedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ConsentStatus status;

    @Column(name = "withdrawal_reason", length = 40)
    private String withdrawalReason;

    @Column(name = "granted_at", nullable = false, updatable = false)
    private Instant grantedAt;

    @Column(name = "withdrawn_at")
    private Instant withdrawnAt;
}

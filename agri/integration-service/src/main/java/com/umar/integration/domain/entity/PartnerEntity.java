package com.umar.integration.domain.entity;

import com.umar.enums.integration.DataScopeLevel;
import com.umar.integration.domain.enums.PartnerStatus;
import com.umar.enums.integration.PartnerType;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "partners", indexes = {
        @Index(name = "idx_partners_reg_no", columnList = "registrationNumber", unique = true),
        @Index(name = "idx_partners_tenant", columnList = "tenantId")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PartnerEntity {

    @Id
    @Column(length = 64)
    private String partnerId;

    @Column(length = 64)
    private String tenantId;

    @Column(nullable = false, length = 200)
    private String legalName;

    @Column(nullable = false, length = 200)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PartnerType type;

    @Column(nullable = false, unique = true, length = 100)
    private String registrationNumber;

    @Lob
    @Column(nullable = false)
    private String countryCodesJson;

    @Lob
    @Column(nullable = false)
    private String contactsJson;

    @Lob
    @Column(nullable = false)
    private String allowedEventTypesJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DataScopeLevel dataScope;

    @Column(length = 500)
    private String dataScopeJustification;

    @Lob
    @Column(nullable = false)
    private String visibilityJson;

    @Lob
    private String connectorRightsJson;

    @Lob
    private String extensionPointsJson;

    @Lob
    @Column(nullable = false)
    private String quotasJson;

    @Lob
    private String networkJson;

    @Lob
    @Column(nullable = false)
    private String agreementTokensJson;

    @Lob
    @Column(nullable = false)
    private String agreementHashesJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PartnerStatus status;

    @Column(nullable = false)
    private Integer version;

    @Column(nullable = false, length = 128)
    private String partnerHash;

    @Column(length = 64)
    private String approvalId;

    @Column(length = 128)
    private String oauthClientId;

    @Column(length = 128)
    private String mtlsProfileId;

    @Column(nullable = false, length = 128)
    private String createdBy;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;
}

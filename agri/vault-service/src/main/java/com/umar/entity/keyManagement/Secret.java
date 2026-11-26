package com.umar.entity.keyManagement;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "SECRET")
@EntityListeners(AuditingEntityListener.class)
@AllArgsConstructor
@NoArgsConstructor
@Data
public class Secret {

    @Id
    private UUID id;

    @Column(name = "REF", nullable = false, unique = true)
    private String ref;

    @Column(name = "NAME")
    private String name;

    @Column(name = "SECRET_CLASS")
    private String secretClass;

    @Column(name = "MODE")
    private String mode; // GENERATE | PROVIDE | DYNAMIC

    @Column(name = "OWNER")
    private String owner;

    @Column(name = "OWNER_TYPE")
    private String ownerType;

    @Column(name = "OWNER_ID")
    private String ownerId;

    @Column(name = "ALLOWED_USES")
    private String allowedUses; // comma-separated

    @Column(name = "ALLOWED_SERVICES")
    private String allowedServices; // comma-separated

    @Column(name = "ALLOWED_PURPOSES")
    private String allowedPurposes; // comma-separated

    @Column(name = "OVERLAP_HOURS")
    private Integer overlapHours = 24;

    @Column(name = "ROTATION_INTERVAL_DAYS")
    private Integer rotationIntervalDays;

    @Column(name = "NOTIFY_DAYS_BEFORE_EXPIRY")
    private Integer notifyDaysBeforeExpiry;

    @Column(name = "NEXT_ROTATION_AT")
    private Instant nextRotationAt;

    @Column(name = "PROVIDER_REF")
    private String providerRef;

    @Column(name = "DYNAMIC_SCOPES")
    private String dynamicScopes;

    @Column(name = "DYNAMIC_TTL_SECONDS")
    private Integer dynamicTtlSeconds;

    @Column(name = "STATUS", nullable = false)
    private String status = "ACTIVE";

    @Column(name = "CREATED_AT", nullable = false)
    private Instant createdAt = Instant.now();
}

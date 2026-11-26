package com.umar.auth.domain.entity;

import com.umar.enums.auth.RegistrationChannel;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_sessions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshSessionEntity {

    @Id
    @Column(name = "token_id", nullable = false, updatable = false)
    private UUID tokenId;

    @Column(name = "session_family_id", nullable = false)
    private UUID sessionFamilyId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 128)
    private String tokenHash;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "device_id", length = 128)
    private String deviceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    private RegistrationChannel channel;

    @Column(name = "status", nullable = false, length = 20)
    private String status; // ACTIVE | USED | REVOKED

    @Column(name = "suspicious", nullable = false)
    private boolean suspicious;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "idle_expires_at", nullable = false)
    private Instant idleExpiresAt;

    @Column(name = "replaced_by")
    private UUID replacedBy;
}

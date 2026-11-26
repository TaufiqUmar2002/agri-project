package com.umar.auth.domain.entity;

import com.umar.enums.auth.AccountStatus;
import com.umar.enums.auth.AuthRole;
import com.umar.enums.auth.KycTier;
import com.umar.enums.auth.RegistrationChannel;
import com.umar.enums.auth.UserType;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Entity
@Table(name = "user_accounts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserAccountEntity {

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "user_type", nullable = false, length = 20)
    private UserType userType;

    @Column(name = "phone_token", length = 128)
    private String phoneToken;

    @Column(name = "phone_hash", unique = true, length = 128)
    private String phoneHash;

    @Column(name = "username", unique = true, length = 80)
    private String username;

    @Convert(converter = com.umar.crypto.VaultEncryptedStringConverter.class)
    @Column(name = "full_name_enc", nullable = false, columnDefinition = "TEXT")
    private String fullNameEnc;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private AccountStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "kyc_tier", nullable = false, length = 16)
    private KycTier kycTier;

    @Column(name = "roles_csv", nullable = false, length = 512)
    private String rolesCsv;

    @Column(name = "pin_hash", length = 256)
    private String pinHash;

    @Column(name = "password_hash", length = 256)
    private String passwordHash;

    @Column(name = "mfa_secret_ref", length = 128)
    private String mfaSecretRef;

    @Column(name = "preferred_language", nullable = false, length = 8)
    private String preferredLanguage;

    @Enumerated(EnumType.STRING)
    @Column(name = "registered_via", length = 20)
    private RegistrationChannel registeredVia;

    @Column(name = "agent_id")
    private UUID agentId;

    @Column(name = "cooperative_id", length = 64)
    private String cooperativeId;

    @Column(name = "failed_login_count", nullable = false)
    private int failedLoginCount;

    @Column(name = "lockout_count", nullable = false)
    private int lockoutCount;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    public Set<AuthRole> getRoles() {
        if (rolesCsv == null || rolesCsv.isBlank()) {
            return EnumSet.noneOf(AuthRole.class);
        }
        return Arrays.stream(rolesCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(AuthRole::valueOf)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(AuthRole.class)));
    }

    public void setRoles(Collection<AuthRole> roles) {
        if (roles == null || roles.isEmpty()) {
            this.rolesCsv = "";
        } else {
            this.rolesCsv = roles.stream().map(Enum::name).sorted().collect(Collectors.joining(","));
        }
    }
}

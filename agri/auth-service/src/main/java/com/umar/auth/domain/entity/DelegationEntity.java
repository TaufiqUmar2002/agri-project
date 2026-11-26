package com.umar.auth.domain.entity;

import com.umar.enums.auth.DelegationScope;
import com.umar.enums.auth.DelegationStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Entity
@Table(name = "delegations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DelegationEntity {

    @Id
    @Column(name = "delegation_id", nullable = false, updatable = false)
    private UUID delegationId;

    @Column(name = "agent_id", nullable = false)
    private UUID agentId;

    @Column(name = "farmer_user_id", nullable = false)
    private UUID farmerUserId;

    @Column(name = "scopes_csv", nullable = false, length = 256)
    private String scopesCsv;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private DelegationStatus status;

    @Column(name = "cooperative_id", length = 64)
    private String cooperativeId;

    @Column(name = "location_lat")
    private Double locationLat;

    @Column(name = "location_lon")
    private Double locationLon;

    @Column(name = "location_accuracy")
    private Double locationAccuracy;

    @Column(name = "validity_hours", nullable = false)
    private int validityHours;

    @Column(name = "confirmation_expires_at", nullable = false)
    private Instant confirmationExpiresAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "valid_until")
    private Instant validUntil;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Set<DelegationScope> getScopes() {
        if (scopesCsv == null || scopesCsv.isBlank()) {
            return EnumSet.noneOf(DelegationScope.class);
        }
        return Arrays.stream(scopesCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(DelegationScope::valueOf)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(DelegationScope.class)));
    }

    public void setScopes(Collection<DelegationScope> scopes) {
        this.scopesCsv = scopes.stream().map(Enum::name).sorted().collect(Collectors.joining(","));
    }
}

package com.umar.farmer.domain.entity;

import com.umar.enums.farmer.MembershipStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "group_memberships")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupMembershipEntity {

    @Id
    @Column(name = "membership_id", nullable = false, updatable = false)
    private UUID membershipId;

    @Column(name = "group_id", nullable = false)
    private UUID groupId;

    @Column(name = "farmer_id")
    private UUID farmerId;

    @Column(name = "phone_hash", length = 128)
    private String phoneHash;

    @Column(name = "phone_token", length = 128)
    private String phoneToken;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private MembershipStatus status;

    @Column(name = "invited_at", nullable = false)
    private Instant invitedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;
}

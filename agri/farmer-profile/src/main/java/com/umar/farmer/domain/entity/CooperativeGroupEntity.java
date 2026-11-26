package com.umar.farmer.domain.entity;

import com.umar.enums.farmer.GroupStatus;
import com.umar.enums.farmer.GroupType;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "cooperative_groups")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CooperativeGroupEntity {

    @Id
    @Column(name = "group_id", nullable = false, updatable = false)
    private UUID groupId;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "group_type", nullable = false, length = 32)
    private GroupType groupType;

    @Column(name = "registration_number", length = 40)
    private String registrationNumber;

    @Column(name = "country", nullable = false, length = 8)
    private String country;

    @Column(name = "district", nullable = false, length = 64)
    private String district;

    @Column(name = "leader_user_id", nullable = false)
    private UUID leaderUserId;

    @Column(name = "sponsor_org_id", length = 64)
    private String sponsorOrgId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private GroupStatus status;

    @Column(name = "max_members", nullable = false)
    private int maxMembers;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}

package com.umar.farmer.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "profile_histories")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileHistoryEntity {

    @Id
    @Column(name = "history_id", nullable = false, updatable = false)
    private UUID historyId;

    @Column(name = "farmer_id", nullable = false)
    private UUID farmerId;

    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "changed_fields_csv", nullable = false, length = 512)
    private String changedFieldsCsv;

    @Column(name = "diff_json", nullable = false, columnDefinition = "TEXT")
    private String diffJson;

    @Column(name = "actor", nullable = false, length = 64)
    private String actor;

    @Column(name = "delegation_id", length = 64)
    private String delegationId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}

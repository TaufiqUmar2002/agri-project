package com.umar.notification.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "suppression_list")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class SuppressionEntryEntity {
    @Id
    private UUID id;
    @Column(name = "msisdn_hash", nullable = false)
    private String msisdnHash;
    @Column(nullable = false)
    private String channel;
    @Column(nullable = false)
    private String category;
    @Column(nullable = false)
    private String reason;
    @Column(nullable = false)
    private boolean active;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}

package com.umar.notification.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "broadcast_alerts")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class BroadcastAlertEntity {
    @Id
    private UUID id;
    @Column(name = "broadcast_id", nullable = false, unique = true)
    private String broadcastId;
    @Column(name = "alert_type", nullable = false)
    private String alertType;
    @Column(nullable = false)
    private String severity;
    @Column(name = "audience_json", nullable = false, columnDefinition = "TEXT")
    private String audienceJson;
    @Column(name = "template_code", nullable = false)
    private String templateCode;
    @Column(name = "actions_json", columnDefinition = "TEXT")
    private String actionsJson;
    @Column(name = "variables_json", columnDefinition = "TEXT")
    private String variablesJson;
    @Column(nullable = false)
    private String status;
    @Column(name = "target_count", nullable = false)
    private long targetCount;
    @Column(name = "estimated_cost", nullable = false)
    private BigDecimal estimatedCost;
    @Column(name = "proposal_hash")
    private String proposalHash;
    @Column(name = "created_by")
    private String createdBy;
    @Column(name = "approved_by")
    private String approvedBy;
    @Column(name = "schedule_at")
    private Instant scheduleAt;
    @Column(name = "expires_at")
    private Instant expiresAt;
    @Column(name = "sent_count", nullable = false)
    private long sentCount;
    @Column(name = "delivered_count", nullable = false)
    private long deliveredCount;
    @Column(name = "failed_count", nullable = false)
    private long failedCount;
    @Column(name = "suppressed_count", nullable = false)
    private long suppressedCount;
    @Version
    private int version;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}

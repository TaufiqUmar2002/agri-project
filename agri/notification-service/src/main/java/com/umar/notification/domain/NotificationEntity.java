package com.umar.notification.domain;

import com.umar.enums.notification.NotificationCategory;
import com.umar.enums.notification.NotificationStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class NotificationEntity {
    @Id
    private UUID id;
    @Column(name = "notification_id", nullable = false, unique = true)
    private String notificationId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationCategory category;
    @Column(name = "template_code", nullable = false)
    private String templateCode;
    @Column(name = "template_version", nullable = false)
    private int templateVersion;
    @Column(name = "farmer_id")
    private String farmerId;
    @Column(name = "recipient_type", nullable = false)
    private String recipientType;
    @Column(name = "recipient_ref", nullable = false)
    private String recipientRef;
    @Column(name = "msisdn_hash")
    private String msisdnHash;
    @Column(nullable = false)
    private String language;
    @Column(name = "language_fallback", nullable = false)
    private boolean languageFallback;
    @Column(name = "channel_plan_json", nullable = false, columnDefinition = "TEXT")
    private String channelPlanJson;
    @Column(name = "current_channel")
    private String currentChannel;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationStatus status;
    @Column(name = "deferred_until")
    private Instant deferredUntil;
    @Column(name = "suppression_reason")
    private String suppressionReason;
    @Column(name = "reason_code")
    private String reasonCode;
    @Convert(converter = com.umar.crypto.VaultEncryptedStringConverter.class)
    @Column(name = "variables_json", columnDefinition = "TEXT")
    private String variablesJson;
    @Convert(converter = com.umar.crypto.VaultEncryptedStringConverter.class)
    @Column(name = "rendered_content", columnDefinition = "TEXT")
    private String renderedContent;
    @Column(name = "secret_token_ref")
    private String secretTokenRef;
    @Column(name = "source_ref")
    private String sourceRef;
    @Column(name = "source_event_id")
    private String sourceEventId;
    @Column(name = "dedupe_key")
    private String dedupeKey;
    @Column(name = "broadcast_id")
    private String broadcastId;
    @Column(name = "failover_json", columnDefinition = "TEXT")
    private String failoverJson;
    @Version
    private int version;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "sent_at")
    private Instant sentAt;
    @Column(name = "delivered_at")
    private Instant deliveredAt;
    @Column(name = "expires_at")
    private Instant expiresAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}

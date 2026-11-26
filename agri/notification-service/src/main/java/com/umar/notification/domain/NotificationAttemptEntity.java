package com.umar.notification.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification_attempts")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class NotificationAttemptEntity {
    @Id
    private UUID id;
    @Column(name = "attempt_id", nullable = false, unique = true)
    private String attemptId;
    @Column(name = "notification_id", nullable = false)
    private String notificationId;
    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;
    @Column(nullable = false)
    private String channel;
    @Column(nullable = false)
    private String aggregator;
    @Column(name = "provider_message_id")
    private String providerMessageId;
    @Column(nullable = false)
    private String status;
    @Column(name = "reason_code")
    private String reasonCode;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}

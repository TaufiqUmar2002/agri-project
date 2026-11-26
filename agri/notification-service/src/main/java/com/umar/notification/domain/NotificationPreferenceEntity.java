package com.umar.notification.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification_preferences")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class NotificationPreferenceEntity {
    @Id
    private UUID id;
    @Column(name = "farmer_id", nullable = false, unique = true)
    private String farmerId;
    @Column(nullable = false)
    private String language;
    @Column(name = "channels_json", nullable = false, columnDefinition = "TEXT")
    private String channelsJson;
    @Column(name = "whatsapp_opt_in", nullable = false)
    private boolean whatsappOptIn;
    @Column(name = "opt_out_json", nullable = false, columnDefinition = "TEXT")
    private String optOutJson;
    @Column(name = "quiet_hours_json", columnDefinition = "TEXT")
    private String quietHoursJson;
    @Column(name = "consent_json", columnDefinition = "TEXT")
    private String consentJson;
    @Column(name = "history_json", columnDefinition = "TEXT")
    private String historyJson;
    @Column(nullable = false)
    private int version;
    @Column(name = "updated_by")
    private String updatedBy;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}

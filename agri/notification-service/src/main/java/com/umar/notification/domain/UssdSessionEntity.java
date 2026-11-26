package com.umar.notification.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ussd_sessions")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class UssdSessionEntity {
    @Id
    private UUID id;
    @Column(name = "session_id", nullable = false, unique = true)
    private String sessionId;
    @Column(name = "service_code", nullable = false)
    private String serviceCode;
    @Column(name = "msisdn_hash", nullable = false)
    private String msisdnHash;
    @Column(name = "farmer_id")
    private String farmerId;
    @Column(name = "network_code")
    private String networkCode;
    @Column(nullable = false)
    private String state;
    @Column(name = "menu_data_json", columnDefinition = "TEXT")
    private String menuDataJson;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}

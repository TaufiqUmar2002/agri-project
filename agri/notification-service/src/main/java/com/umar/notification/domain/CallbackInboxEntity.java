package com.umar.notification.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "callback_inbox")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class CallbackInboxEntity {
    @Id
    private UUID id;
    @Column(nullable = false, unique = true)
    private String nonce;
    @Column(nullable = false)
    private String aggregator;
    @Column(name = "event_type", nullable = false)
    private String eventType;
    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;
}

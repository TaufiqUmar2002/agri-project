package com.umar.payout.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payout_outbox_events")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PayoutOutboxEventEntity {
    @Id
    @Column(name = "event_id")
    private UUID eventId;

    @Column(nullable = false)
    private String topic;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "aggregate_key", nullable = false)
    private String aggregateKey;

    @Column(name = "payload_json", nullable = false, columnDefinition = "TEXT")
    private String payloadJson;

    @Column(nullable = false)
    private boolean published;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}

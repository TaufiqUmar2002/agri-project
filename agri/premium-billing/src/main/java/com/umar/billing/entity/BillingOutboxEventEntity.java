package com.umar.billing.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "billing_outbox_events")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class BillingOutboxEventEntity {
    @Id
    @Column(name = "event_id")
    private UUID eventId;
    @Column(name = "topic", nullable = false, length = 128)
    private String topic;
    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;
    @Column(name = "aggregate_id", nullable = false, length = 64)
    private String aggregateId;
    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;
    @Column(name = "published", nullable = false)
    private boolean published;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}

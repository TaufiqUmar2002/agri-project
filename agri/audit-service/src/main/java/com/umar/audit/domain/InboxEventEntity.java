package com.umar.audit.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inbox_events")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class InboxEventEntity {
    @Id
    private UUID id;
    @Column(name = "event_id", nullable = false, unique = true)
    private String eventId;
    @Column(nullable = false)
    private String topic;
    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;
}

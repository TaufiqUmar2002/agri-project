package com.umar.payout.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "payout_inbox_events")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PayoutInboxEventEntity {
    @Id
    @Column(name = "event_id")
    private String eventId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;
}

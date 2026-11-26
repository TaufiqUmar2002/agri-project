package com.umar.integration.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "domain_event_archive", indexes = {
        @Index(name = "idx_archive_occurred", columnList = "occurredAt"),
        @Index(name = "idx_archive_pos", columnList = "kafkaPartition,kafkaOffset")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DomainEventArchiveEntity {

    @Id
    @Column(length = 128)
    private String eventId;

    @Column(nullable = false, length = 100)
    private String eventType;

    @Column(nullable = false, length = 128)
    private String kafkaTopic;

    @Column(nullable = false)
    private Integer kafkaPartition;

    @Column(nullable = false)
    private Long kafkaOffset;

    @Column(length = 128)
    private String subjectKey;

    @Lob
    @Column(nullable = false)
    private String envelopeJson;

    @Column(nullable = false)
    private Instant occurredAt;

    @Column(nullable = false)
    private Instant archivedAt;
}

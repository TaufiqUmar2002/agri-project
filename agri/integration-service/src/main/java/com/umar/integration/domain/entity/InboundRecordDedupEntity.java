package com.umar.integration.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "inbound_record_dedup", uniqueConstraints = {
        @UniqueConstraint(name = "uk_inbound_dedup", columnNames = {"connectorId", "eventType", "externalId"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InboundRecordDedupEntity {

    @Id
    @Column(length = 96)
    private String id;

    @Column(nullable = false, length = 64)
    private String connectorId;

    @Column(nullable = false, length = 100)
    private String eventType;

    @Column(nullable = false, length = 120)
    private String externalId;

    @Column(nullable = false, length = 64)
    private String receiptId;

    @Column(nullable = false)
    private Instant createdAt;
}

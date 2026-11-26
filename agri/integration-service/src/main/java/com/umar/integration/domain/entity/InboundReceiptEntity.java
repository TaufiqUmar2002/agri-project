package com.umar.integration.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "inbound_receipts", indexes = {
        @Index(name = "idx_receipt_conn_time", columnList = "connectorId,receivedAt"),
        @Index(name = "idx_receipt_owner_time", columnList = "ownerId,receivedAt")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InboundReceiptEntity {

    @Id
    @Column(length = 64)
    private String receiptId;

    @Column(nullable = false, length = 64)
    private String connectorId;

    @Column(nullable = false, length = 64)
    private String ownerId;

    @Column(nullable = false, length = 100)
    private String eventType;

    @Column(length = 100)
    private String sourceId;

    @Column(nullable = false, length = 32)
    private String status; // ACCEPTED | PARTIALLY_ACCEPTED | REJECTED

    @Column(nullable = false)
    private Integer receivedCount;

    @Column(nullable = false)
    private Integer acceptedCount;

    @Column(nullable = false)
    private Integer duplicateCount;

    @Column(nullable = false)
    private Integer rejectedCount;

    @Lob
    private String outcomesJson;

    @Column(nullable = false)
    private Instant receivedAt;
}

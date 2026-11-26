package com.umar.billing.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "callback_inbox",
        uniqueConstraints = @UniqueConstraint(name = "uq_inbox_provider_txn", columnNames = {"provider", "provider_txn_id"}))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class CallbackInboxEntity {
    @Id
    @Column(name = "inbox_id")
    private UUID inboxId;
    @Column(name = "provider", nullable = false, length = 32)
    private String provider;
    @Column(name = "provider_txn_id", nullable = false, length = 80)
    private String providerTxnId;
    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;
    @Column(name = "signature_valid", nullable = false)
    private boolean signatureValid;
    @Column(name = "source_ip", length = 64)
    private String sourceIp;
    @Column(name = "payload_sanitized", nullable = false, columnDefinition = "TEXT")
    private String payloadSanitized;
    @Column(name = "status", nullable = false, length = 24)
    private String status;
    @Column(name = "ack_body", nullable = false, columnDefinition = "TEXT")
    private String ackBody;
}

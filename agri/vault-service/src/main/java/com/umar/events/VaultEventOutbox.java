package com.umar.events;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Durable outbox record for a vault domain event. Events are written here in
 * the same transaction as the triggering operation, then delivered to consumers.
 */
@Entity
@Table(name = "VAULT_EVENT_OUTBOX")
@AllArgsConstructor
@NoArgsConstructor
@Data
public class VaultEventOutbox {

    @Id
    private UUID id;

    @Column(name = "EVENT_TYPE", nullable = false)
    private String eventType;

    @Column(name = "PAYLOAD", columnDefinition = "TEXT")
    private String payload;

    @Column(name = "CORRELATION_ID")
    private String correlationId;

    @Column(name = "STATUS", nullable = false)
    private String status = "PENDING";

    @Column(name = "CREATED_AT", nullable = false)
    private Instant createdAt = Instant.now();
}

package com.umar.events;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * A vault domain event propagated to consumers (section 2.14).
 *
 * @param id            unique event id
 * @param type          the event type / topic
 * @param payload       event-specific attributes (never contains plaintext secrets)
 * @param correlationId correlation id for tracing across services
 * @param timestamp     when the event occurred
 */
public record VaultEvent(
        UUID id,
        VaultEventType type,
        Map<String, Object> payload,
        String correlationId,
        Instant timestamp
) {
    public static VaultEvent of(VaultEventType type, Map<String, Object> payload, String correlationId) {
        return new VaultEvent(UUID.randomUUID(), type, payload, correlationId, Instant.now());
    }
}

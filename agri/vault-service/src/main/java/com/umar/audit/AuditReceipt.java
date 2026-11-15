package com.umar.audit;

import java.time.Instant;

public record AuditReceipt(
        String externalReference,
        String contentSha256,
        Instant acknowledgedAt,
        boolean durable
) {
    public AuditReceipt {
        if (!durable) throw new IllegalArgumentException("Receipt must confirm durable acceptance");
    }
}

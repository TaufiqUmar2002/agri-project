package com.umar.audit;


import java.time.Instant;
import java.util.UUID;

public record AuditEvent(
        UUID id,
        String eventType,
        String operation,
        String callerService,
        String callerIdentity,
        String purpose,
        String dataClass,
        String policyId,
        Long policyVersion,
        UUID resourceId,
        String correlationId,
        String outcome,
        String errorCode,
        Instant eventTimestamp
) {
    public AuditEvent {
        if (id == null) throw new IllegalArgumentException("id is required");
        if (eventType == null || eventType.isBlank()) throw new IllegalArgumentException("eventType is required");
        if (operation == null || operation.isBlank()) throw new IllegalArgumentException("operation is required");
        if (outcome == null || !outcome.matches("SUCCESS|DENIED|FAILED")) {
            throw new IllegalArgumentException("Invalid audit outcome");
        }
        if (eventTimestamp == null) throw new IllegalArgumentException("eventTimestamp is required");
    }
}

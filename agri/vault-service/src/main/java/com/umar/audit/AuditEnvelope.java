package com.umar.audit;

public record AuditEnvelope(
        AuditEvent event,
        long sequenceNumber,
        String previousHash,
        String recordHash,
        String canonicalEvent
) {}

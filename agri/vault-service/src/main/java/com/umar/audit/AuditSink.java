package com.umar.audit;

public interface AuditSink {
    /**
     * Idempotent append. A return value means durable acceptance.
     * A duplicate ID must be accepted only after verifying identical content.
     */
    AuditReceipt append(AuditEnvelope envelope);
}

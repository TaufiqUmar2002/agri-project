package com.umar.events;

/**
 * Domain event types for event-driven propagation of changes (section 2.14).
 */
public enum VaultEventType {

    /** Values tokenized or documents stored (aggregated by class and caller). */
    TOKENIZED("vault.tokenized"),

    /** Any access; SIEM and audit receive a copy. */
    ACCESS_LOGGED("vault.access.logged"),

    /** Unusual access volume; security alerted, service may be throttled. */
    ACCESS_ANOMALY("vault.access.anomaly"),

    /** Key rotated; auth refreshes signing keys (JWKS), audit records rotation. */
    KEY_ROTATED("vault.key.rotated"),

    /** Erasure completed; data-rights orchestrator confirms to the person. */
    ERASURE_COMPLETED("vault.erasure.completed"),

    /** Break-glass issued or used; security and compliance alerted. */
    BREAKGLASS_USED("vault.breakglass.used"),

    /** Policy changed; client libraries refresh policies, audit records change. */
    POLICY_CHANGED("vault.policy.changed"),

    /** Secret version created/rotated; audit records versioning. */
    SECRET_VERSIONED("vault.secret.versioned"),

    /** Legal hold changed; audit records change. */
    LEGALHOLD_CHANGED("vault.legalhold.changed"),

    /** Backup verified; audit records verification. */
    BACKUP_VERIFIED("vault.backup.verified");

    private final String topic;

    VaultEventType(String topic) {
        this.topic = topic;
    }

    public String topic() {
        return topic;
    }
}

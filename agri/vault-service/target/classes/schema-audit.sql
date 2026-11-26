-- Audit journal schema (hash-chained, durable). Source of truth for audit events.
CREATE SCHEMA IF NOT EXISTS vault_audit;

CREATE TABLE IF NOT EXISTS vault_audit.audit_chain_head (
    chain_name   VARCHAR(50) PRIMARY KEY,
    last_sequence BIGINT      NOT NULL,
    last_hash    VARCHAR(64)  NOT NULL,
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO vault_audit.audit_chain_head (chain_name, last_sequence, last_hash)
VALUES ('main', 0, '0000000000000000000000000000000000000000000000000000000000000000')
ON CONFLICT (chain_name) DO NOTHING;

CREATE TABLE IF NOT EXISTS vault_audit.audit_journal (
    id               UUID PRIMARY KEY,
    sequence_number  BIGINT      NOT NULL UNIQUE,
    canonical_event  TEXT        NOT NULL,
    previous_hash    VARCHAR(64) NOT NULL,
    record_hash      VARCHAR(64) NOT NULL
);

CREATE TABLE IF NOT EXISTS vault_audit.audit_delivery (
    audit_id           UUID PRIMARY KEY REFERENCES vault_audit.audit_journal(id),
    status             VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    external_reference TEXT,
    attempt_count      INT         NOT NULL DEFAULT 0,
    last_error_code    VARCHAR(100),
    next_attempt_at    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    delivered_at       TIMESTAMP,
    created_at         TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_audit_delivery_due
    ON vault_audit.audit_delivery (status, next_attempt_at);

CREATE TABLE IF NOT EXISTS vault_audit.audit_delivery_attempt (
    id           BIGSERIAL PRIMARY KEY,
    audit_id     UUID        NOT NULL REFERENCES vault_audit.audit_journal(id),
    result       VARCHAR(20) NOT NULL,
    error_code   VARCHAR(100),
    external_ref TEXT,
    attempted_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

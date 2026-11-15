
-- ============================================================
-- V1: DATA VAULT FOUNDATIONAL DATABASE SCHEMA
-- PostgreSQL
-- ============================================================

-- 1. RETENTION CLASS
CREATE TABLE retention_class (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    retention_group VARCHAR(100) NOT NULL UNIQUE,
    period_days INTEGER,
    minimum_days INTEGER,
    legal_basis VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_retention_period
        CHECK (period_days IS NULL OR period_days >= 0),

    CONSTRAINT chk_minimum_days
        CHECK (minimum_days IS NULL OR minimum_days >= 0),

    CONSTRAINT chk_retention_status
        CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

-- 2. DATA CLASS
CREATE TABLE data_class (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    value_kind VARCHAR(20) NOT NULL,
    validation JSONB NOT NULL DEFAULT '{}'::jsonb,
    masking JSONB NOT NULL DEFAULT '{}'::jsonb,
    classification VARCHAR(50) NOT NULL,
    retention_group VARCHAR(100) NOT NULL,
    blind_index BOOLEAN NOT NULL DEFAULT FALSE,
    fpe BOOLEAN NOT NULL DEFAULT FALSE,
    detokenizable BOOLEAN NOT NULL DEFAULT FALSE,
    allowed_purposes TEXT[] NOT NULL DEFAULT '{}',
    version INTEGER NOT NULL DEFAULT 1,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_data_class_name_version
        UNIQUE (name, version),

    CONSTRAINT fk_data_class_retention
        FOREIGN KEY (retention_group)
        REFERENCES retention_class(retention_group),

    CONSTRAINT chk_data_class_kind
        CHECK (value_kind IN ('VALUE', 'DOCUMENT', 'SECRET')),

    CONSTRAINT chk_data_class_status
        CHECK (status IN ('ACTIVE', 'INACTIVE', 'PENDING')),

    CONSTRAINT chk_data_class_version
        CHECK (version > 0)
);

CREATE INDEX idx_data_class_status
ON data_class(status);

CREATE INDEX idx_data_class_retention
ON data_class(retention_group);

-- 3. ACCESS POLICY
CREATE TABLE access_policy (
    id UUID PRIMARY KEY,
    service_identity VARCHAR(150) NOT NULL,
    data_class_id UUID NOT NULL,
    purposes TEXT[] NOT NULL,
    operations TEXT[] NOT NULL,
    requests_per_minute INTEGER NOT NULL DEFAULT 60,
    max_batch_size INTEGER NOT NULL DEFAULT 100,
    conditions JSONB NOT NULL DEFAULT '{}'::jsonb,
    version INTEGER NOT NULL DEFAULT 1,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    approved_by VARCHAR(150),
    approved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_policy_data_class
        FOREIGN KEY (data_class_id)
        REFERENCES data_class(id),

    CONSTRAINT chk_policy_rate
        CHECK (requests_per_minute > 0),

    CONSTRAINT chk_policy_batch
        CHECK (max_batch_size BETWEEN 1 AND 100),

    CONSTRAINT chk_policy_status
        CHECK (status IN ('PENDING', 'ACTIVE', 'REVOKED')),

    CONSTRAINT chk_policy_version
        CHECK (version > 0),

    CONSTRAINT uq_policy_version
        UNIQUE (
            service_identity,
            data_class_id,
            version
        )
);

CREATE INDEX idx_policy_lookup
ON access_policy(
    service_identity,
    data_class_id,
    status
);

-- 4. KEY VERSION
CREATE TABLE key_version (
    id UUID PRIMARY KEY,
    level VARCHAR(30) NOT NULL,
    version INTEGER NOT NULL,
    state VARCHAR(20) NOT NULL,
    hsm_ref VARCHAR(500) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    activated_at TIMESTAMPTZ,
    retired_at TIMESTAMPTZ,

    CONSTRAINT uq_key_level_version
        UNIQUE(level, version),

    CONSTRAINT chk_key_level
        CHECK (
            level IN (
                'ROOT',
                'CLASS_KEK',
                'SUBJECT_DEK',
                'INDEX',
                'SIGNING'
            )
        ),

    CONSTRAINT chk_key_state
        CHECK (
            state IN (
                'ACTIVE',
                'RETIRING',
                'RETIRED',
                'DESTROYED'
            )
        ),

    CONSTRAINT chk_key_version
        CHECK (version > 0)
);

-- 5. SUBJECT KEY
CREATE TABLE subject_key (
    id UUID PRIMARY KEY,
    subject_ref VARCHAR(200) NOT NULL,
    retention_group VARCHAR(100) NOT NULL,
    wrapped_key BYTEA NOT NULL,
    class_kek_version_id UUID NOT NULL,
    state VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    shredded_at TIMESTAMPTZ,

    CONSTRAINT fk_subject_key_kek
        FOREIGN KEY (class_kek_version_id)
        REFERENCES key_version(id),

    CONSTRAINT fk_subject_retention
        FOREIGN KEY (retention_group)
        REFERENCES retention_class(retention_group),

    CONSTRAINT uq_subject_retention
        UNIQUE(subject_ref, retention_group),

    CONSTRAINT chk_subject_key_state
        CHECK (state IN ('ACTIVE', 'SHREDDED'))
);

CREATE INDEX idx_subject_key_subject
ON subject_key(subject_ref);

-- 6. VAULT TOKEN
CREATE TABLE vault_token (
    id UUID PRIMARY KEY,
    token VARCHAR(300) NOT NULL UNIQUE,
    data_class_id UUID NOT NULL,
    subject_key_id UUID NOT NULL,
    subject_ref VARCHAR(200) NOT NULL,
    retention_group VARCHAR(100) NOT NULL,
    ciphertext BYTEA NOT NULL,
    nonce BYTEA NOT NULL,
    key_version_id UUID NOT NULL,
    masked_form VARCHAR(200),
    token_format VARCHAR(30) NOT NULL DEFAULT 'OPAQUE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_token_class
        FOREIGN KEY (data_class_id)
        REFERENCES data_class(id),

    CONSTRAINT fk_token_subject_key
        FOREIGN KEY (subject_key_id)
        REFERENCES subject_key(id),

    CONSTRAINT fk_token_key_version
        FOREIGN KEY (key_version_id)
        REFERENCES key_version(id),

    CONSTRAINT fk_token_retention
        FOREIGN KEY (retention_group)
        REFERENCES retention_class(retention_group),

    CONSTRAINT chk_token_format
        CHECK (token_format IN ('OPAQUE', 'FPE')),

    CONSTRAINT chk_token_nonce
        CHECK (octet_length(nonce) = 12)
);

CREATE INDEX idx_token_subject
ON vault_token(subject_ref, retention_group);

CREATE INDEX idx_token_class
ON vault_token(data_class_id);

CREATE INDEX idx_token_subject_key
ON vault_token(subject_key_id);

-- 7. BLIND INDEX ENTRY
CREATE TABLE blind_index_entry (
    id UUID PRIMARY KEY,
    data_class_id UUID NOT NULL,
    index_key_version_id UUID NOT NULL,
    hash BYTEA NOT NULL,
    subject_ref VARCHAR(200) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_index_class
        FOREIGN KEY (data_class_id)
        REFERENCES data_class(id),

    CONSTRAINT fk_index_key
        FOREIGN KEY (index_key_version_id)
        REFERENCES key_version(id),

    CONSTRAINT uq_blind_index
        UNIQUE (
            data_class_id,
            index_key_version_id,
            hash,
            subject_ref
        )
);

CREATE INDEX idx_blind_hash_lookup
ON blind_index_entry(
    data_class_id,
    index_key_version_id,
    hash
);

-- 8. IDEMPOTENCY RECORD
CREATE TABLE idempotency_record (
    id UUID PRIMARY KEY,
    service_identity VARCHAR(150) NOT NULL,
    idempotency_key VARCHAR(200) NOT NULL,
    request_hash BYTEA NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',
    response_payload JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT uq_idempotency
        UNIQUE(service_identity, idempotency_key),

    CONSTRAINT chk_idempotency_status
        CHECK (
            status IN (
                'IN_PROGRESS',
                'COMPLETED',
                'FAILED'
            )
        )
);

CREATE INDEX idx_idempotency_expiry
ON idempotency_record(expires_at);

-- 9. ACCESS RECORD
CREATE TABLE access_record (
    record_id UUID PRIMARY KEY,
    occurred_at TIMESTAMPTZ NOT NULL,
    caller VARCHAR(150) NOT NULL,
    operation VARCHAR(100) NOT NULL,
    data_class VARCHAR(100),
    secret_ref VARCHAR(200),
    purpose VARCHAR(150) NOT NULL,
    context_ref_hash BYTEA,
    subject_hash BYTEA,
    record_count INTEGER NOT NULL DEFAULT 1,
    outcome VARCHAR(30) NOT NULL,
    previous_hash BYTEA,
    record_hash BYTEA NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_access_count
        CHECK (record_count > 0),

    CONSTRAINT chk_access_outcome
        CHECK (
            outcome IN (
                'SUCCESS',
                'DENIED',
                'FAILED'
            )
        )
);

CREATE INDEX idx_access_caller_time
ON access_record(caller, occurred_at DESC);

CREATE INDEX idx_access_purpose
ON access_record(purpose, occurred_at DESC);

CREATE INDEX idx_access_record_hash
ON access_record(record_hash);

-- 10. AUDIT OUTBOX
CREATE TABLE audit_outbox (
    id UUID PRIMARY KEY,
    access_record_id UUID NOT NULL,
    event_id UUID NOT NULL UNIQUE,
    payload JSONB NOT NULL,
    payload_hash BYTEA NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_retry_at TIMESTAMPTZ,
    worm_object_ref VARCHAR(1000),
    worm_acknowledged_at TIMESTAMPTZ,
    last_error_code VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_outbox_access_record
        FOREIGN KEY (access_record_id)
        REFERENCES access_record(record_id),

    CONSTRAINT chk_outbox_status
        CHECK (
            status IN (
                'PENDING',
                'IN_PROGRESS',
                'ACKNOWLEDGED',
                'FAILED'
            )
        ),

    CONSTRAINT chk_outbox_attempts
        CHECK (attempt_count >= 0),

    CONSTRAINT chk_outbox_ack
        CHECK (
            status <> 'ACKNOWLEDGED'
            OR (
                worm_object_ref IS NOT NULL
                AND worm_acknowledged_at IS NOT NULL
            )
        )
);

CREATE INDEX idx_audit_outbox_pending
ON audit_outbox(status, next_retry_at, created_at);

CREATE UNIQUE INDEX uq_audit_outbox_record
ON audit_outbox(access_record_id);

-- 11. ERASURE REGISTRY
CREATE TABLE erasure_registry (
    id UUID PRIMARY KEY,
    subject_hash BYTEA NOT NULL,
    retention_group VARCHAR(100) NOT NULL,
    request_id VARCHAR(200) NOT NULL,
    shredded_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_erasure_retention
        FOREIGN KEY (retention_group)
        REFERENCES retention_class(retention_group),

    CONSTRAINT uq_erasure_subject_group
        UNIQUE(subject_hash, retention_group),

    CONSTRAINT uq_erasure_request
        UNIQUE(request_id, retention_group)
);

-- 12. LEGAL HOLD
CREATE TABLE legal_hold (
    id UUID PRIMARY KEY,
    case_ref_hash BYTEA NOT NULL,
    authority VARCHAR(100) NOT NULL,
    scope JSONB NOT NULL,
    status VARCHAR(30) NOT NULL,
    reason VARCHAR(1000),
    review_date DATE NOT NULL,
    placed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    lifted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_legal_hold_status
        CHECK (
            status IN (
                'ACTIVE',
                'LIFT_PENDING',
                'LIFTED',
                'EXPIRED'
            )
        )
);

CREATE INDEX idx_legal_hold_status
ON legal_hold(status, review_date);

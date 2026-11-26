CREATE TABLE audit_records (
    id UUID PRIMARY KEY,
    record_id VARCHAR(64) NOT NULL UNIQUE,
    chain_id VARCHAR(24) NOT NULL,
    seq BIGINT NOT NULL,
    retention_class VARCHAR(32) NOT NULL,
    event_id VARCHAR(128) NOT NULL,
    event_type VARCHAR(96) NOT NULL,
    producer_service VARCHAR(64) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ingested_at TIMESTAMP WITH TIME ZONE NOT NULL,
    subject_type VARCHAR(32),
    subject_id VARCHAR(128),
    claim_id VARCHAR(64),
    policy_id VARCHAR(64),
    plot_id VARCHAR(64),
    actor_id VARCHAR(64),
    payload_json TEXT NOT NULL,
    flags_json TEXT,
    redacted BOOLEAN NOT NULL DEFAULT FALSE,
    prev_hash VARCHAR(128) NOT NULL,
    record_hash VARCHAR(128) NOT NULL,
    checkpoint_id VARCHAR(64),
    CONSTRAINT uq_chain_seq UNIQUE (chain_id, seq)
);
CREATE INDEX idx_audit_claim ON audit_records (claim_id);
CREATE INDEX idx_audit_subject ON audit_records (subject_id, occurred_at);

CREATE TABLE audit_subject_edges (
    id UUID PRIMARY KEY,
    subject_type VARCHAR(32) NOT NULL,
    subject_id VARCHAR(128) NOT NULL,
    linked_type VARCHAR(32) NOT NULL,
    linked_id VARCHAR(128) NOT NULL,
    record_id VARCHAR(64) NOT NULL,
    relation_type VARCHAR(48) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_edge_subject ON audit_subject_edges (subject_type, subject_id);

CREATE TABLE audit_checkpoints (
    id UUID PRIMARY KEY,
    checkpoint_id VARCHAR(64) NOT NULL UNIQUE,
    chain_id VARCHAR(24) NOT NULL,
    from_seq BIGINT NOT NULL,
    to_seq BIGINT NOT NULL,
    merkle_root VARCHAR(128) NOT NULL,
    head_hash VARCHAR(128) NOT NULL,
    signature VARCHAR(256) NOT NULL,
    key_id VARCHAR(64) NOT NULL,
    signed_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE telemetry_batches (
    id UUID PRIMARY KEY,
    batch_id VARCHAR(64) NOT NULL UNIQUE,
    device_id VARCHAR(64) NOT NULL,
    window_start TIMESTAMP WITH TIME ZONE NOT NULL,
    window_end TIMESTAMP WITH TIME ZONE NOT NULL,
    reading_count BIGINT NOT NULL,
    merkle_root VARCHAR(128),
    leaves_json TEXT NOT NULL,
    sealed BOOLEAN NOT NULL DEFAULT FALSE,
    checkpoint_id VARCHAR(64),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE regulatory_reports (
    id UUID PRIMARY KEY,
    report_id VARCHAR(64) NOT NULL UNIQUE,
    report_type VARCHAR(48) NOT NULL,
    format VARCHAR(16) NOT NULL,
    status VARCHAR(24) NOT NULL,
    schedule_id VARCHAR(64),
    period_from VARCHAR(32),
    period_to VARCHAR(32),
    filters_json TEXT,
    checkpoints_json TEXT,
    row_counts_json TEXT,
    totals_json TEXT,
    report_hash VARCHAR(128) NOT NULL,
    created_by VARCHAR(64),
    approved_by VARCHAR(64),
    next_run_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE model_register (
    id UUID PRIMARY KEY,
    model_id VARCHAR(64) NOT NULL,
    version VARCHAR(32) NOT NULL,
    component VARCHAR(64) NOT NULL,
    approval_status VARCHAR(24) NOT NULL,
    rule_hash VARCHAR(128) NOT NULL,
    approved_by VARCHAR(64),
    governance_notes TEXT,
    effective_from TIMESTAMP WITH TIME ZONE NOT NULL,
    effective_to TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE verification_runs (
    id UUID PRIMARY KEY,
    run_id VARCHAR(64) NOT NULL UNIQUE,
    scope VARCHAR(32) NOT NULL,
    chain_id VARCHAR(24),
    claim_id VARCHAR(64),
    mode VARCHAR(16) NOT NULL,
    status VARCHAR(24) NOT NULL,
    checked_records BIGINT NOT NULL,
    mismatch_count BIGINT NOT NULL,
    details_json TEXT,
    initiated_by VARCHAR(64) NOT NULL,
    reason VARCHAR(128) NOT NULL,
    note VARCHAR(512) NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE audit_findings (
    id UUID PRIMARY KEY,
    finding_id VARCHAR(64) NOT NULL UNIQUE,
    rule_code VARCHAR(64) NOT NULL,
    severity VARCHAR(24) NOT NULL,
    status VARCHAR(24) NOT NULL,
    subject_type VARCHAR(32),
    subject_id VARCHAR(128),
    claim_id VARCHAR(64),
    producer_service VARCHAR(64),
    summary VARCHAR(512) NOT NULL,
    details_json TEXT,
    evidence_refs_json TEXT,
    pending_action VARCHAR(32),
    proposal_hash VARCHAR(128),
    proposed_by VARCHAR(64),
    approved_by VARCHAR(64),
    accept_until DATE,
    history_json TEXT,
    version INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE legal_holds (
    id UUID PRIMARY KEY,
    hold_id VARCHAR(64) NOT NULL UNIQUE,
    status VARCHAR(32) NOT NULL,
    case_ref VARCHAR(128) NOT NULL,
    reason_code VARCHAR(64) NOT NULL,
    scope_json TEXT NOT NULL,
    resolved_json TEXT,
    placed_by VARCHAR(64) NOT NULL,
    release_proposed_by VARCHAR(64),
    release_approved_by VARCHAR(64),
    proposal_hash VARCHAR(128),
    note VARCHAR(1024) NOT NULL,
    version INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE inbox_events (
    id UUID PRIMARY KEY,
    event_id VARCHAR(128) NOT NULL UNIQUE,
    topic VARCHAR(128) NOT NULL,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    event_type VARCHAR(96) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    topic VARCHAR(128) NOT NULL,
    payload TEXT NOT NULL,
    published BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE
);

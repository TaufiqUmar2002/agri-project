CREATE TABLE IF NOT EXISTS payout_instructions (
    payout_id VARCHAR(64) PRIMARY KEY,
    claim_id VARCHAR(64) NOT NULL,
    attempt INTEGER NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL UNIQUE,
    originator_ref VARCHAR(128) NOT NULL UNIQUE,
    status VARCHAR(32) NOT NULL,
    escalated BOOLEAN NOT NULL DEFAULT FALSE,
    cancel_requested BOOLEAN NOT NULL DEFAULT FALSE,
    route VARCHAR(32) NOT NULL,
    connection VARCHAR(64),
    amount_minor BIGINT NOT NULL,
    currency VARCHAR(8) NOT NULL DEFAULT 'KES',
    destination_token VARCHAR(128),
    destination_mask VARCHAR(64),
    provider_ref VARCHAR(128),
    provider_ref_masked VARCHAR(64),
    attempts_json TEXT NOT NULL DEFAULT '[]',
    fallback_count INTEGER NOT NULL DEFAULT 0,
    reason_code VARCHAR(64),
    retryable BOOLEAN,
    hold_reason VARCHAR(64),
    cash_out_agent_id VARCHAR(64),
    cash_out_area VARCHAR(64),
    voucher_hash VARCHAR(128),
    voucher_status VARCHAR(32),
    voucher_expires_at TIMESTAMP WITH TIME ZONE,
    statement_match_status VARCHAR(32),
    statement_date DATE,
    next_step VARCHAR(64),
    next_check_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    initiated_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    hours_to_result DOUBLE PRECISION,
    version_num INTEGER NOT NULL DEFAULT 1
);

CREATE INDEX IF NOT EXISTS idx_payout_claim ON payout_instructions (claim_id);
CREATE INDEX IF NOT EXISTS idx_payout_status ON payout_instructions (status);

CREATE TABLE IF NOT EXISTS payout_provider_connections (
    connection_id VARCHAR(64) PRIMARY KEY,
    provider VARCHAR(64) NOT NULL,
    network VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    priority_order INTEGER NOT NULL,
    last_probe_at TIMESTAMP WITH TIME ZONE,
    last_probe_ok BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version_num INTEGER NOT NULL DEFAULT 1
);

CREATE TABLE IF NOT EXISTS payout_cash_out_agents (
    agent_id VARCHAR(64) PRIMARY KEY,
    area_code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    max_single_minor BIGINT NOT NULL DEFAULT 500000
);

CREATE TABLE IF NOT EXISTS payout_callback_inbox (
    inbox_id UUID PRIMARY KEY,
    provider VARCHAR(64) NOT NULL,
    nonce VARCHAR(128) NOT NULL,
    provider_ref VARCHAR(128),
    body_hash VARCHAR(128) NOT NULL,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(32) NOT NULL,
    CONSTRAINT uk_pcb_provider_nonce UNIQUE (provider, nonce)
);

CREATE TABLE IF NOT EXISTS payout_statement_imports (
    import_id VARCHAR(64) PRIMARY KEY,
    connection VARCHAR(64) NOT NULL,
    statement_date DATE NOT NULL,
    checksum VARCHAR(128) NOT NULL,
    total_lines INTEGER NOT NULL,
    matched_lines INTEGER NOT NULL,
    unmatched_lines INTEGER NOT NULL,
    total_minor BIGINT NOT NULL,
    fees_minor BIGINT NOT NULL,
    breaks_count INTEGER NOT NULL,
    imported_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_psi_conn_date UNIQUE (connection, statement_date)
);

CREATE TABLE IF NOT EXISTS payout_settlement_batches (
    batch_id VARCHAR(64) PRIMARY KEY,
    connection VARCHAR(64) NOT NULL,
    settlement_date DATE NOT NULL,
    currency VARCHAR(8) NOT NULL,
    gross_minor BIGINT NOT NULL,
    fees_minor BIGINT NOT NULL,
    net_minor BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    matched_count INTEGER NOT NULL,
    unmatched_count INTEGER NOT NULL,
    checksum VARCHAR(128) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS payout_outbox_events (
    event_id UUID PRIMARY KEY,
    topic VARCHAR(128) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    aggregate_key VARCHAR(128) NOT NULL,
    payload_json TEXT NOT NULL,
    published BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS payout_idempotency (
    idempotency_key VARCHAR(128) PRIMARY KEY,
    operation VARCHAR(64) NOT NULL,
    request_hash VARCHAR(128) NOT NULL,
    response_status INTEGER NOT NULL,
    response_body TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS payout_inbox_events (
    event_id VARCHAR(128) PRIMARY KEY,
    event_type VARCHAR(64) NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL
);

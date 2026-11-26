CREATE TABLE IF NOT EXISTS risk_pools (
    pool_id VARCHAR(64) PRIMARY KEY,
    pool_code VARCHAR(64) NOT NULL UNIQUE,
    name_json TEXT NOT NULL,
    insurer_ref VARCHAR(128) NOT NULL,
    country_code VARCHAR(8) NOT NULL,
    currency VARCHAR(8) NOT NULL,
    mappings_json TEXT NOT NULL,
    accounting_json TEXT NOT NULL,
    limits_json TEXT NOT NULL,
    alerts_json TEXT NOT NULL DEFAULT '[]',
    status VARCHAR(32) NOT NULL,
    config_hash VARCHAR(128) NOT NULL,
    version_num INTEGER NOT NULL DEFAULT 1,
    alert_version INTEGER NOT NULL DEFAULT 1,
    author_id VARCHAR(128) NOT NULL,
    effective_from DATE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS reinsurance_treaties (
    treaty_id VARCHAR(64) PRIMARY KEY,
    pool_id VARCHAR(64) NOT NULL,
    treaty_code VARCHAR(64) NOT NULL,
    version_num INTEGER NOT NULL,
    type VARCHAR(32) NOT NULL,
    placements_json TEXT NOT NULL,
    period_json TEXT NOT NULL,
    scope_json TEXT NOT NULL,
    terms_json TEXT NOT NULL,
    settlement_json TEXT NOT NULL,
    wording_token VARCHAR(128) NOT NULL,
    treaty_hash VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    author_id VARCHAR(128) NOT NULL,
    effective_from DATE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS pool_premium_register (
    policy_id VARCHAR(64) PRIMARY KEY,
    pool_id VARCHAR(64) NOT NULL,
    product_id VARCHAR(64) NOT NULL,
    region_code VARCHAR(64) NOT NULL,
    peril VARCHAR(32) NOT NULL,
    season VARCHAR(32) NOT NULL,
    sum_insured_minor BIGINT NOT NULL DEFAULT 0,
    written_minor BIGINT NOT NULL DEFAULT 0,
    subsidy_minor BIGINT NOT NULL DEFAULT 0,
    paid_minor BIGINT NOT NULL DEFAULT 0,
    earned_minor BIGINT NOT NULL DEFAULT 0,
    refunded_minor BIGINT NOT NULL DEFAULT 0,
    currency VARCHAR(8) NOT NULL,
    in_force BOOLEAN NOT NULL DEFAULT FALSE,
    coverage_start DATE,
    coverage_end DATE
);

CREATE TABLE IF NOT EXISTS pool_plot_dimensions (
    plot_id VARCHAR(64) PRIMARY KEY,
    region_code VARCHAR(64) NOT NULL,
    geohash5 VARCHAR(16),
    area_ha DOUBLE PRECISION NOT NULL DEFAULT 1.0,
    crop_code VARCHAR(32)
);

CREATE TABLE IF NOT EXISTS pool_claim_reserves (
    claim_id VARCHAR(64) PRIMARY KEY,
    pool_id VARCHAR(64) NOT NULL,
    policy_id VARCHAR(64) NOT NULL,
    reserved_minor BIGINT NOT NULL DEFAULT 0,
    paid_minor BIGINT NOT NULL DEFAULT 0,
    currency VARCHAR(8) NOT NULL,
    released BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS pool_journal_transactions (
    transaction_id VARCHAR(64) PRIMARY KEY,
    pool_id VARCHAR(64) NOT NULL,
    source_type VARCHAR(64) NOT NULL,
    source_event_id VARCHAR(128) NOT NULL,
    journal_type VARCHAR(32),
    value_date DATE NOT NULL,
    posting_date DATE NOT NULL,
    prior_period BOOLEAN NOT NULL DEFAULT FALSE,
    total_minor BIGINT NOT NULL,
    currency VARCHAR(8) NOT NULL,
    lines_json TEXT NOT NULL,
    dimensions_json TEXT,
    reverses_tx_id VARCHAR(64),
    reversed_by_tx_id VARCHAR(64),
    suspense_reason VARCHAR(64),
    prev_hash VARCHAR(128) NOT NULL,
    entry_hash VARCHAR(128) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_rp_tx_src UNIQUE (source_type, source_event_id)
);

CREATE TABLE IF NOT EXISTS pool_manual_journals (
    journal_id VARCHAR(64) PRIMARY KEY,
    pool_id VARCHAR(64) NOT NULL,
    journal_type VARCHAR(32) NOT NULL,
    value_date DATE NOT NULL,
    currency VARCHAR(8) NOT NULL,
    total_minor BIGINT NOT NULL,
    lines_json TEXT NOT NULL,
    reverses VARCHAR(64),
    reason_code VARCHAR(64) NOT NULL,
    evidence_tokens_csv TEXT NOT NULL,
    note TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    approval_id VARCHAR(64),
    maker_id VARCHAR(128) NOT NULL,
    posted_tx_id VARCHAR(64),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS pool_periods (
    period_key VARCHAR(96) PRIMARY KEY,
    pool_id VARCHAR(64) NOT NULL,
    period VARCHAR(16) NOT NULL,
    status VARCHAR(32) NOT NULL,
    earned_minor BIGINT NOT NULL DEFAULT 0,
    ceded_earned_minor BIGINT NOT NULL DEFAULT 0,
    trial_balance_hash VARCHAR(128),
    close_package_token VARCHAR(128)
);

CREATE TABLE IF NOT EXISTS pool_approvals (
    approval_id VARCHAR(64) PRIMARY KEY,
    type VARCHAR(32) NOT NULL,
    subject_id VARCHAR(64) NOT NULL,
    subject_hash VARCHAR(128) NOT NULL,
    requested_by VARCHAR(128) NOT NULL,
    required_levels INTEGER NOT NULL,
    required_roles_csv VARCHAR(128) NOT NULL,
    decisions_json TEXT NOT NULL DEFAULT '[]',
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS pool_capacity_reservations (
    reservation_id VARCHAR(64) PRIMARY KEY,
    policy_id VARCHAR(64) NOT NULL UNIQUE,
    pool_id VARCHAR(64) NOT NULL,
    region_code VARCHAR(64) NOT NULL,
    peril VARCHAR(32) NOT NULL,
    season VARCHAR(32) NOT NULL,
    sum_insured_minor BIGINT NOT NULL,
    currency VARCHAR(8) NOT NULL,
    status VARCHAR(32) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS pool_projections (
    event_id VARCHAR(64) PRIMARY KEY,
    pool_id VARCHAR(64) NOT NULL,
    region_code VARCHAR(64) NOT NULL,
    peril VARCHAR(32) NOT NULL,
    indicated_payout_minor BIGINT NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS riskpool_outbox_events (
    event_id UUID PRIMARY KEY,
    topic VARCHAR(128) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    aggregate_key VARCHAR(128) NOT NULL,
    payload_json TEXT NOT NULL,
    published BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS riskpool_idempotency (
    idempotency_key VARCHAR(128) PRIMARY KEY,
    operation VARCHAR(64) NOT NULL,
    request_hash VARCHAR(128) NOT NULL,
    response_status INTEGER NOT NULL,
    response_body TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS riskpool_inbox_events (
    event_id VARCHAR(128) PRIMARY KEY,
    event_type VARCHAR(64) NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL
);

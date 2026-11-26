CREATE TABLE IF NOT EXISTS trigger_rules (
    rule_id VARCHAR(64) NOT NULL,
    version_num INTEGER NOT NULL,
    rule_code VARCHAR(64) NOT NULL,
    product_id VARCHAR(64) NOT NULL,
    product_version INTEGER NOT NULL,
    contractual_definition_hash VARCHAR(128) NOT NULL,
    definition_hash VARCHAR(128) NOT NULL,
    peril VARCHAR(32) NOT NULL,
    region_codes_csv TEXT NOT NULL,
    index_type VARCHAR(64) NOT NULL,
    observation_window_json TEXT,
    data_sources_json TEXT,
    corroboration_json TEXT,
    phases_json TEXT,
    tiers_json TEXT NOT NULL,
    quality_gates_json TEXT,
    cooling_period_json TEXT,
    grace_period_hours INTEGER NOT NULL DEFAULT 72,
    backtest_ref VARCHAR(128),
    status VARCHAR(32) NOT NULL,
    author_id VARCHAR(128) NOT NULL,
    approvals_json TEXT NOT NULL DEFAULT '[]',
    effective_from DATE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    activated_at TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (rule_id, version_num)
);

CREATE INDEX IF NOT EXISTS idx_trg_rule_code_status ON trigger_rules (rule_code, status);

CREATE TABLE IF NOT EXISTS trigger_evaluations (
    evaluation_id VARCHAR(64) PRIMARY KEY,
    rule_id VARCHAR(64) NOT NULL,
    rule_version INTEGER NOT NULL,
    evaluation_type VARCHAR(32) NOT NULL,
    region_codes_csv TEXT NOT NULL,
    window_id VARCHAR(64) NOT NULL,
    window_start DATE NOT NULL,
    window_end DATE NOT NULL,
    status VARCHAR(32) NOT NULL,
    evaluated_count INTEGER NOT NULL DEFAULT 0,
    breached_count INTEGER NOT NULL DEFAULT 0,
    review_required_count INTEGER NOT NULL DEFAULT 0,
    reason TEXT,
    triggered_by VARCHAR(128) NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE IF NOT EXISTS trigger_decisions (
    decision_id VARCHAR(64) PRIMARY KEY,
    evaluation_id VARCHAR(64) NOT NULL,
    policy_id VARCHAR(64) NOT NULL,
    farmer_id VARCHAR(64) NOT NULL,
    plot_id VARCHAR(64) NOT NULL,
    region_code VARCHAR(64) NOT NULL,
    rule_id VARCHAR(64) NOT NULL,
    rule_version INTEGER NOT NULL,
    rule_hash VARCHAR(128) NOT NULL,
    window_id VARCHAR(64) NOT NULL,
    window_start DATE NOT NULL,
    window_end DATE NOT NULL,
    outcome VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    tier_id VARCHAR(64),
    payout_percent DOUBLE PRECISION NOT NULL DEFAULT 0,
    indicated_payout_minor BIGINT NOT NULL DEFAULT 0,
    currency VARCHAR(8) NOT NULL DEFAULT 'KES',
    computed_index_value DOUBLE PRECISION NOT NULL,
    threshold_value DOUBLE PRECISION NOT NULL,
    data_quality VARCHAR(32) NOT NULL,
    evidence_hash VARCHAR(128) NOT NULL,
    evidence_bundle_token VARCHAR(128) NOT NULL,
    evidence_json TEXT NOT NULL,
    phases_json TEXT NOT NULL,
    corroboration_json TEXT NOT NULL,
    history_json TEXT NOT NULL DEFAULT '[]',
    large_event BOOLEAN NOT NULL DEFAULT FALSE,
    first_reviewer_id VARCHAR(128),
    decided_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version_num INTEGER NOT NULL DEFAULT 1,
    CONSTRAINT uk_policy_window UNIQUE (policy_id, window_id, rule_id)
);

CREATE INDEX IF NOT EXISTS idx_trg_dec_eval ON trigger_decisions (evaluation_id);
CREATE INDEX IF NOT EXISTS idx_trg_dec_farmer ON trigger_decisions (farmer_id);

CREATE TABLE IF NOT EXISTS trigger_simulation_runs (
    simulation_id VARCHAR(64) PRIMARY KEY,
    rule_id VARCHAR(64),
    rule_version INTEGER,
    definition_hash VARCHAR(128) NOT NULL,
    region_codes_csv TEXT NOT NULL,
    from_date DATE NOT NULL,
    to_date DATE NOT NULL,
    seasons_covered INTEGER NOT NULL,
    expected_loss_ratio DOUBLE PRECISION NOT NULL,
    burning_cost_percent DOUBLE PRECISION NOT NULL,
    data_coverage_percent DOUBLE PRECISION NOT NULL,
    summary_json TEXT NOT NULL,
    per_season_json TEXT NOT NULL,
    comparison_json TEXT,
    sensitivity_json TEXT,
    report_token VARCHAR(128),
    valid_until DATE NOT NULL,
    requested_by VARCHAR(128) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS trigger_policy_bindings (
    policy_id VARCHAR(64) PRIMARY KEY,
    farmer_id VARCHAR(64) NOT NULL,
    plot_id VARCHAR(64) NOT NULL,
    region_code VARCHAR(64) NOT NULL,
    product_id VARCHAR(64) NOT NULL,
    rule_id VARCHAR(64) NOT NULL,
    rule_version INTEGER NOT NULL,
    sum_insured_minor BIGINT NOT NULL,
    currency VARCHAR(8) NOT NULL DEFAULT 'KES',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    activated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS trigger_observations (
    ingest_id VARCHAR(128) PRIMARY KEY,
    source_id VARCHAR(64) NOT NULL,
    region_code VARCHAR(64) NOT NULL,
    plot_id VARCHAR(64),
    channel VARCHAR(64) NOT NULL,
    metric_value DOUBLE PRECISION NOT NULL,
    observed_date DATE NOT NULL,
    quality_flags_csv TEXT,
    revision INTEGER NOT NULL DEFAULT 0,
    ingested_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS trigger_event_summary (
    event_id VARCHAR(64) PRIMARY KEY,
    rule_id VARCHAR(64) NOT NULL,
    rule_version INTEGER NOT NULL,
    peril VARCHAR(32) NOT NULL,
    region_code VARCHAR(64) NOT NULL,
    window_id VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    affected_policies INTEGER NOT NULL,
    breached_policies INTEGER NOT NULL,
    indicated_payout_minor BIGINT NOT NULL,
    currency VARCHAR(8) NOT NULL DEFAULT 'KES',
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS trigger_outbox_events (
    event_id UUID PRIMARY KEY,
    topic VARCHAR(128) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    aggregate_key VARCHAR(128) NOT NULL,
    payload_json TEXT NOT NULL,
    published BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS trigger_idempotency (
    idempotency_key VARCHAR(128) PRIMARY KEY,
    operation VARCHAR(64) NOT NULL,
    request_hash VARCHAR(128) NOT NULL,
    response_status INTEGER NOT NULL,
    response_body TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS trigger_inbox_events (
    event_id VARCHAR(128) PRIMARY KEY,
    event_type VARCHAR(64) NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL
);

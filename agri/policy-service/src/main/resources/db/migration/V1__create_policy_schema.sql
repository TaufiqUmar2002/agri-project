CREATE TABLE IF NOT EXISTS product_versions (
    id VARCHAR(64) PRIMARY KEY,
    product_id VARCHAR(40) NOT NULL,
    product_code VARCHAR(40) NOT NULL,
    version_num INT NOT NULL,
    status VARCHAR(24) NOT NULL,
    peril VARCHAR(32) NOT NULL,
    definition_json TEXT NOT NULL,
    definition_hash VARCHAR(64) NOT NULL,
    diff_json TEXT,
    backtest_ref VARCHAR(64),
    backtest_json TEXT,
    author_id VARCHAR(64) NOT NULL,
    effective_from DATE NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE,
    retired_at TIMESTAMP WITH TIME ZONE,
    successor_product_id VARCHAR(40),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_product_version UNIQUE (product_id, version_num)
);

CREATE TABLE IF NOT EXISTS product_approvals (
    approval_id UUID PRIMARY KEY,
    product_id VARCHAR(40) NOT NULL,
    version_num INT NOT NULL,
    approver_id VARCHAR(64) NOT NULL,
    approver_role VARCHAR(32) NOT NULL,
    decision VARCHAR(24) NOT NULL,
    definition_hash VARCHAR(64) NOT NULL,
    note TEXT NOT NULL,
    decided_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_product_role_approval UNIQUE (product_id, version_num, approver_role)
);

CREATE TABLE IF NOT EXISTS subsidy_schemes (
    scheme_code VARCHAR(40) PRIMARY KEY,
    funder VARCHAR(120) NOT NULL,
    percent_bps INT NOT NULL,
    cap_minor BIGINT,
    budget_minor BIGINT NOT NULL,
    reserved_minor BIGINT NOT NULL DEFAULT 0,
    region_codes_csv TEXT,
    crop_codes_csv TEXT,
    group_ids_csv TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS quotes (
    quote_id VARCHAR(40) PRIMARY KEY,
    farmer_id UUID NOT NULL,
    plot_id VARCHAR(40) NOT NULL,
    product_id VARCHAR(40) NOT NULL,
    product_version INT NOT NULL,
    inputs_hash VARCHAR(64) NOT NULL,
    sum_insured_minor BIGINT NOT NULL,
    currency VARCHAR(8) NOT NULL,
    total_payable_minor BIGINT NOT NULL,
    subsidy_minor BIGINT NOT NULL,
    subsidy_scheme_code VARCHAR(40),
    price_breakdown_json TEXT NOT NULL,
    factors_json TEXT NOT NULL,
    payout_tiers_json TEXT NOT NULL,
    score_band VARCHAR(24) NOT NULL,
    score_source VARCHAR(24) NOT NULL,
    language VARCHAR(8) NOT NULL,
    channel VARCHAR(16) NOT NULL,
    waiting_period_days INT NOT NULL,
    valid_until TIMESTAMP WITH TIME ZONE NOT NULL,
    pay_by TIMESTAMP WITH TIME ZONE NOT NULL,
    used_by_policy_id VARCHAR(40),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS policies (
    policy_id VARCHAR(40) PRIMARY KEY,
    policy_number VARCHAR(40) NOT NULL UNIQUE,
    farmer_id UUID NOT NULL,
    plot_id VARCHAR(40) NOT NULL,
    quote_id VARCHAR(40) NOT NULL UNIQUE,
    product_id VARCHAR(40) NOT NULL,
    product_version INT NOT NULL,
    peril VARCHAR(32) NOT NULL,
    definition_hash VARCHAR(64) NOT NULL,
    terms_snapshot_json TEXT NOT NULL,
    terms_hash VARCHAR(64) NOT NULL,
    status VARCHAR(24) NOT NULL,
    sum_insured_minor BIGINT NOT NULL,
    currency VARCHAR(8) NOT NULL,
    total_payable_minor BIGINT NOT NULL,
    subsidy_minor BIGINT NOT NULL,
    subsidy_scheme_code VARCHAR(40),
    levy_minor BIGINT NOT NULL,
    payer_type VARCHAR(16) NOT NULL,
    premium_status VARCHAR(16) NOT NULL,
    pay_by TIMESTAMP WITH TIME ZONE,
    paid_at TIMESTAMP WITH TIME ZONE,
    waiting_period_days INT NOT NULL,
    waiting_ends_at TIMESTAMP WITH TIME ZONE,
    coverage_start TIMESTAMP WITH TIME ZONE,
    coverage_end TIMESTAMP WITH TIME ZONE,
    payout_state VARCHAR(24) NOT NULL,
    paid_to_date_minor BIGINT NOT NULL DEFAULT 0,
    hold_reasons_csv TEXT,
    summary_text TEXT NOT NULL,
    summary_language VARCHAR(8) NOT NULL,
    terms_version VARCHAR(16) NOT NULL,
    summary_doc_ref VARCHAR(120),
    acceptance_json TEXT NOT NULL,
    attributes_json TEXT,
    region_code VARCHAR(40),
    crop_code VARCHAR(40),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_policy_farmer ON policies(farmer_id);
CREATE INDEX IF NOT EXISTS idx_policy_plot ON policies(plot_id);

CREATE TABLE IF NOT EXISTS policy_history (
    history_id UUID PRIMARY KEY,
    policy_id VARCHAR(40) NOT NULL,
    from_status VARCHAR(24),
    to_status VARCHAR(24) NOT NULL,
    actor VARCHAR(64) NOT NULL,
    reason VARCHAR(255) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS region_enrollment_state (
    region_code VARCHAR(40) PRIMARY KEY,
    status VARCHAR(16) NOT NULL,
    reason VARCHAR(255),
    source_forecast_id VARCHAR(64),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS policy_outbox_events (
    event_id UUID PRIMARY KEY,
    topic VARCHAR(128) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    payload TEXT NOT NULL,
    published BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS policy_idempotency_keys (
    idempotency_key VARCHAR(128) PRIMARY KEY,
    operation VARCHAR(64) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    http_status INT NOT NULL,
    response_body TEXT NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

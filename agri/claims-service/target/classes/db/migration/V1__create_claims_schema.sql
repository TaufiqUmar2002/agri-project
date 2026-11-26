CREATE TABLE IF NOT EXISTS claims (
    claim_id VARCHAR(64) PRIMARY KEY,
    claim_number VARCHAR(64) NOT NULL UNIQUE,
    type VARCHAR(32) NOT NULL,
    policy_id VARCHAR(64) NOT NULL,
    farmer_id VARCHAR(64) NOT NULL,
    plot_id VARCHAR(64) NOT NULL,
    region_code VARCHAR(64) NOT NULL,
    rule_id VARCHAR(64),
    rule_version INTEGER,
    window_id VARCHAR(64),
    tier_id VARCHAR(64),
    decision_id VARCHAR(64),
    status VARCHAR(32) NOT NULL,
    approval_path VARCHAR(32),
    calculated_minor BIGINT NOT NULL DEFAULT 0,
    payable_minor BIGINT NOT NULL DEFAULT 0,
    currency VARCHAR(8) NOT NULL DEFAULT 'KES',
    terms_hash VARCHAR(128),
    claim_hash VARCHAR(128) NOT NULL,
    hold_reasons_csv TEXT,
    next_step VARCHAR(64),
    current_attempt INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL DEFAULT 5,
    cancel_requested BOOLEAN NOT NULL DEFAULT FALSE,
    last_retry_at TIMESTAMP WITH TIME ZONE,
    parent_claim_id VARCHAR(64),
    payout_id VARCHAR(64),
    payout_route VARCHAR(32),
    use_current_destination BOOLEAN NOT NULL DEFAULT FALSE,
    destination_id VARCHAR(64),
    pending_retry_id VARCHAR(64),
    hours_to_pay DOUBLE PRECISION,
    timeline_json TEXT NOT NULL DEFAULT '[]',
    opened_at TIMESTAMP WITH TIME ZONE NOT NULL,
    approved_at TIMESTAMP WITH TIME ZONE,
    paid_at TIMESTAMP WITH TIME ZONE,
    closed_at TIMESTAMP WITH TIME ZONE,
    dispute_window_ends TIMESTAMP WITH TIME ZONE,
    dispute_count INTEGER NOT NULL DEFAULT 0,
    prior_status VARCHAR(32),
    version_num INTEGER NOT NULL DEFAULT 1,
    CONSTRAINT uk_claim_policy_window_tier UNIQUE (policy_id, window_id, tier_id, type)
);

CREATE INDEX IF NOT EXISTS idx_claims_farmer ON claims (farmer_id);
CREATE INDEX IF NOT EXISTS idx_claims_status ON claims (status);

CREATE TABLE IF NOT EXISTS claim_reservations (
    reservation_id VARCHAR(64) PRIMARY KEY,
    claim_id VARCHAR(64) NOT NULL,
    policy_id VARCHAR(64) NOT NULL,
    amount_minor BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS claim_approvals (
    approval_id VARCHAR(64) PRIMARY KEY,
    claim_id VARCHAR(64) NOT NULL,
    retry_id VARCHAR(64),
    approver_id VARCHAR(128) NOT NULL,
    approver_role VARCHAR(64) NOT NULL,
    action VARCHAR(32) NOT NULL,
    note TEXT NOT NULL,
    decided_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS claim_disputes (
    dispute_id VARCHAR(64) PRIMARY KEY,
    claim_id VARCHAR(64) NOT NULL,
    reason_code VARCHAR(64) NOT NULL,
    explanation TEXT NOT NULL,
    photo_tokens_csv TEXT,
    status VARCHAR(32) NOT NULL,
    outcome VARCHAR(32),
    reviewer_id VARCHAR(128),
    first_approver_id VARCHAR(128),
    evidence_reviewed BOOLEAN NOT NULL DEFAULT FALSE,
    supplementary BOOLEAN NOT NULL DEFAULT FALSE,
    supplementary_amount_minor BIGINT,
    supplementary_claim_id VARCHAR(64),
    proposal_hash VARCHAR(128),
    checks_json TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    resolved_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE IF NOT EXISTS claim_confirmations (
    confirmation_id VARCHAR(64) PRIMARY KEY,
    claim_id VARCHAR(64) NOT NULL,
    farmer_id VARCHAR(64) NOT NULL,
    step_up_token VARCHAR(128) NOT NULL,
    acknowledged_amount_minor BIGINT NOT NULL,
    channel VARCHAR(16) NOT NULL,
    confirmed_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS claim_overpayment_cases (
    case_id VARCHAR(64) PRIMARY KEY,
    claim_id VARCHAR(64) NOT NULL,
    policy_id VARCHAR(64) NOT NULL,
    paid_minor BIGINT NOT NULL,
    revised_payable_minor BIGINT NOT NULL,
    excess_minor BIGINT NOT NULL,
    reason_code VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS claim_reconciliation_breaks (
    break_id VARCHAR(64) PRIMARY KEY,
    type VARCHAR(64) NOT NULL,
    severity VARCHAR(32) NOT NULL,
    claim_id VARCHAR(64),
    payout_id VARCHAR(64),
    amount_minor BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    owner VARCHAR(64) NOT NULL,
    region_code VARCHAR(64),
    detected_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS claims_outbox_events (
    event_id UUID PRIMARY KEY,
    topic VARCHAR(128) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    aggregate_key VARCHAR(128) NOT NULL,
    payload_json TEXT NOT NULL,
    published BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS claims_idempotency (
    idempotency_key VARCHAR(128) PRIMARY KEY,
    operation VARCHAR(64) NOT NULL,
    request_hash VARCHAR(128) NOT NULL,
    response_status INTEGER NOT NULL,
    response_body TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS claims_inbox_events (
    event_id VARCHAR(128) PRIMARY KEY,
    event_type VARCHAR(64) NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL
);

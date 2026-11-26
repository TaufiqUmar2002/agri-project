CREATE TABLE IF NOT EXISTS policy_billing_facts (
    policy_id VARCHAR(40) PRIMARY KEY,
    farmer_id VARCHAR(64) NOT NULL,
    product_id VARCHAR(40) NOT NULL,
    premium_minor BIGINT NOT NULL,
    subsidy_minor BIGINT NOT NULL,
    currency VARCHAR(8) NOT NULL,
    payer_type VARCHAR(16) NOT NULL,
    payer_ref VARCHAR(64),
    pay_by TIMESTAMP WITH TIME ZONE,
    status VARCHAR(24) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS invoices (
    invoice_id VARCHAR(40) PRIMARY KEY,
    invoice_number VARCHAR(40) NOT NULL UNIQUE,
    payment_reference VARCHAR(32) NOT NULL UNIQUE,
    type VARCHAR(24) NOT NULL,
    policy_id VARCHAR(40),
    batch_id VARCHAR(40),
    payer_type VARCHAR(16) NOT NULL,
    payer_ref VARCHAR(64) NOT NULL,
    status VARCHAR(24) NOT NULL,
    total_minor BIGINT NOT NULL,
    premium_minor BIGINT NOT NULL,
    paid_minor BIGINT NOT NULL DEFAULT 0,
    outstanding_minor BIGINT NOT NULL,
    currency VARCHAR(8) NOT NULL,
    subsidy_minor BIGINT NOT NULL DEFAULT 0,
    due_date TIMESTAMP WITH TIME ZONE,
    grace_ends_at TIMESTAMP WITH TIME ZONE,
    instalment_plan_id VARCHAR(40),
    plan_hash VARCHAR(64),
    activation_threshold_percent DOUBLE PRECISION NOT NULL DEFAULT 100.0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS invoice_lines (
    line_id UUID PRIMARY KEY,
    invoice_id VARCHAR(40) NOT NULL,
    code VARCHAR(24) NOT NULL,
    label VARCHAR(120) NOT NULL,
    amount_minor BIGINT NOT NULL,
    refundable BOOLEAN NOT NULL,
    funder VARCHAR(120)
);

CREATE TABLE IF NOT EXISTS payment_attempts (
    attempt_id VARCHAR(40) PRIMARY KEY,
    invoice_id VARCHAR(40) NOT NULL,
    method VARCHAR(24) NOT NULL,
    provider VARCHAR(32),
    payer_number_token VARCHAR(80),
    masked_payer VARCHAR(40),
    amount_minor BIGINT NOT NULL,
    status VARCHAR(24) NOT NULL,
    provider_ref VARCHAR(64),
    idempotency_key VARCHAR(128),
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS payments (
    payment_id VARCHAR(40) PRIMARY KEY,
    invoice_id VARCHAR(40) NOT NULL,
    attempt_id VARCHAR(40),
    method VARCHAR(24) NOT NULL,
    source VARCHAR(16) NOT NULL,
    amount_minor BIGINT NOT NULL,
    currency VARCHAR(8) NOT NULL,
    payer_token VARCHAR(80),
    masked_payer VARCHAR(40),
    receipt_number VARCHAR(64),
    statement_line_ref VARCHAR(64),
    received_at TIMESTAMP WITH TIME ZONE NOT NULL,
    provider VARCHAR(32),
    provider_txn_id VARCHAR(80),
    status VARCHAR(24) NOT NULL,
    recorded_by VARCHAR(64),
    CONSTRAINT uq_provider_txn UNIQUE (provider, provider_txn_id)
);

CREATE TABLE IF NOT EXISTS instalment_plans (
    plan_id VARCHAR(40) PRIMARY KEY,
    plan_code VARCHAR(40) NOT NULL,
    version_num INT NOT NULL,
    name_json TEXT NOT NULL,
    scope_json TEXT NOT NULL,
    schedule_json TEXT NOT NULL,
    deposit_percent DOUBLE PRECISION NOT NULL,
    activation_threshold_percent DOUBLE PRECISION NOT NULL,
    min_instalment_minor BIGINT NOT NULL,
    grace_json TEXT NOT NULL,
    lapse_json TEXT NOT NULL,
    reinstatement_json TEXT NOT NULL,
    reminders_json TEXT NOT NULL,
    status VARCHAR(24) NOT NULL,
    plan_hash VARCHAR(64) NOT NULL,
    diff_json TEXT,
    author_id VARCHAR(64) NOT NULL,
    effective_from DATE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_plan_code_version UNIQUE (plan_code, version_num)
);

CREATE TABLE IF NOT EXISTS instalment_schedules (
    schedule_id UUID PRIMARY KEY,
    invoice_id VARCHAR(40) NOT NULL,
    seq INT NOT NULL,
    due_date TIMESTAMP WITH TIME ZONE NOT NULL,
    amount_minor BIGINT NOT NULL,
    paid_minor BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(24) NOT NULL,
    grace_ends_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE IF NOT EXISTS refunds (
    refund_id VARCHAR(40) PRIMARY KEY,
    invoice_id VARCHAR(40) NOT NULL,
    policy_id VARCHAR(40),
    payment_ids_csv TEXT,
    amount_minor BIGINT NOT NULL,
    currency VARCHAR(8) NOT NULL,
    reason_code VARCHAR(32) NOT NULL,
    source_event_id VARCHAR(80) UNIQUE,
    status VARCHAR(24) NOT NULL,
    destination_type VARCHAR(24) NOT NULL,
    destination_token VARCHAR(80),
    masked_destination VARCHAR(64),
    provider VARCHAR(32),
    provider_ref VARCHAR(64),
    approval_id VARCHAR(40),
    requested_by VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS billing_approvals (
    approval_id VARCHAR(40) PRIMARY KEY,
    type VARCHAR(24) NOT NULL,
    subject_id VARCHAR(40) NOT NULL,
    subject_hash VARCHAR(64) NOT NULL,
    requested_by VARCHAR(64) NOT NULL,
    required_levels INT NOT NULL,
    required_roles_csv VARCHAR(120) NOT NULL,
    decisions_json TEXT NOT NULL,
    status VARCHAR(24) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS sponsor_accounts (
    sponsor_id VARCHAR(40) PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    status VARCHAR(24) NOT NULL,
    credit_limit_minor BIGINT NOT NULL DEFAULT 0,
    used_minor BIGINT NOT NULL DEFAULT 0,
    allowed_methods_csv VARCHAR(120) NOT NULL,
    bank_token VARCHAR(80),
    masked_bank VARCHAR(64)
);

CREATE TABLE IF NOT EXISTS sponsor_batches (
    batch_id VARCHAR(40) PRIMARY KEY,
    batch_reference VARCHAR(40) NOT NULL UNIQUE,
    sponsor_id VARCHAR(40) NOT NULL,
    payment_method VARCHAR(24) NOT NULL,
    allocation_mode VARCHAR(24) NOT NULL,
    consolidated_invoice_id VARCHAR(40),
    policy_ids_csv TEXT NOT NULL,
    total_minor BIGINT NOT NULL,
    currency VARCHAR(8) NOT NULL,
    status VARCHAR(24) NOT NULL,
    due_date TIMESTAMP WITH TIME ZONE NOT NULL,
    external_batch_ref VARCHAR(60),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS agent_floats (
    agent_id VARCHAR(64) PRIMARY KEY,
    limit_minor BIGINT NOT NULL,
    balance_minor BIGINT NOT NULL,
    currency VARCHAR(8) NOT NULL,
    collector_active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS callback_inbox (
    inbox_id UUID PRIMARY KEY,
    provider VARCHAR(32) NOT NULL,
    provider_txn_id VARCHAR(80) NOT NULL,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL,
    signature_valid BOOLEAN NOT NULL,
    source_ip VARCHAR(64),
    payload_sanitized TEXT NOT NULL,
    status VARCHAR(24) NOT NULL,
    ack_body TEXT NOT NULL,
    CONSTRAINT uq_inbox_provider_txn UNIQUE (provider, provider_txn_id)
);

CREATE TABLE IF NOT EXISTS reconciliation_runs (
    run_id VARCHAR(48) PRIMARY KEY,
    source VARCHAR(32) NOT NULL,
    statement_date DATE NOT NULL,
    checksum VARCHAR(64),
    total_lines INT NOT NULL,
    matched_lines INT NOT NULL,
    unmatched_lines INT NOT NULL,
    summary_json TEXT NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS reconciliation_breaks (
    break_id VARCHAR(40) PRIMARY KEY,
    run_id VARCHAR(48) NOT NULL,
    type VARCHAR(32) NOT NULL,
    severity VARCHAR(16) NOT NULL,
    status VARCHAR(24) NOT NULL,
    provider_txn_id VARCHAR(80),
    payment_id VARCHAR(40),
    amount_minor BIGINT NOT NULL,
    detail TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS journal_entries (
    entry_id UUID PRIMARY KEY,
    account VARCHAR(40) NOT NULL,
    debit_minor BIGINT NOT NULL,
    credit_minor BIGINT NOT NULL,
    currency VARCHAR(8) NOT NULL,
    reference VARCHAR(64) NOT NULL,
    posted_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS billing_outbox_events (
    event_id UUID PRIMARY KEY,
    topic VARCHAR(128) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    payload TEXT NOT NULL,
    published BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS billing_idempotency_keys (
    idempotency_key VARCHAR(128) PRIMARY KEY,
    operation VARCHAR(64) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    http_status INT NOT NULL,
    response_body TEXT NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

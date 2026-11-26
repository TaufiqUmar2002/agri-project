CREATE TABLE IF NOT EXISTS user_accounts (
    user_id UUID PRIMARY KEY,
    user_type VARCHAR(20) NOT NULL,
    phone_token VARCHAR(128),
    phone_hash VARCHAR(128) UNIQUE,
    username VARCHAR(80) UNIQUE,
    full_name_enc TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    kyc_tier VARCHAR(16) NOT NULL,
    roles_csv VARCHAR(512) NOT NULL,
    pin_hash VARCHAR(256),
    password_hash VARCHAR(256),
    mfa_secret_ref VARCHAR(128),
    preferred_language VARCHAR(8) NOT NULL,
    registered_via VARCHAR(20),
    agent_id UUID,
    cooperative_id VARCHAR(64),
    failed_login_count INT NOT NULL DEFAULT 0,
    lockout_count INT NOT NULL DEFAULT 0,
    locked_until TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    activated_at TIMESTAMPTZ,
    last_login_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS kyc_cases (
    kyc_id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES user_accounts(user_id),
    id_type VARCHAR(32) NOT NULL,
    id_number_token VARCHAR(128) NOT NULL,
    id_number_hash VARCHAR(128) NOT NULL,
    front_doc_token VARCHAR(128) NOT NULL,
    back_doc_token VARCHAR(128),
    selfie_doc_token VARCHAR(128) NOT NULL,
    target_tier VARCHAR(16) NOT NULL,
    status VARCHAR(32) NOT NULL,
    match_score NUMERIC(5, 4),
    provider_ref VARCHAR(128),
    flags_csv VARCHAR(512),
    reason_codes_csv VARCHAR(512),
    submitted_by UUID NOT NULL,
    delegation_id UUID,
    reviewer_id VARCHAR(64),
    second_reviewer_id VARCHAR(64),
    second_review_pending BOOLEAN NOT NULL DEFAULT FALSE,
    reviewer_note VARCHAR(1000),
    lock_holder VARCHAR(64),
    locked_until TIMESTAMPTZ,
    submitted_at TIMESTAMPTZ NOT NULL,
    decided_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS consent_records (
    consent_id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES user_accounts(user_id),
    purpose VARCHAR(40) NOT NULL,
    text_version VARCHAR(32) NOT NULL,
    language VARCHAR(8) NOT NULL,
    channel VARCHAR(20) NOT NULL,
    evidence_type VARCHAR(32) NOT NULL,
    evidence_ref VARCHAR(128),
    partner_id VARCHAR(64),
    captured_by UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    withdrawal_reason VARCHAR(40),
    granted_at TIMESTAMPTZ NOT NULL,
    withdrawn_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS delegations (
    delegation_id UUID PRIMARY KEY,
    agent_id UUID NOT NULL,
    farmer_user_id UUID NOT NULL,
    scopes_csv VARCHAR(256) NOT NULL,
    status VARCHAR(40) NOT NULL,
    cooperative_id VARCHAR(64),
    location_lat DOUBLE PRECISION,
    location_lon DOUBLE PRECISION,
    location_accuracy DOUBLE PRECISION,
    validity_hours INT NOT NULL,
    confirmation_expires_at TIMESTAMPTZ NOT NULL,
    confirmed_at TIMESTAMPTZ,
    valid_until TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS refresh_sessions (
    token_id UUID PRIMARY KEY,
    session_family_id UUID NOT NULL,
    token_hash VARCHAR(128) NOT NULL UNIQUE,
    user_id UUID NOT NULL REFERENCES user_accounts(user_id),
    device_id VARCHAR(128),
    channel VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    suspicious BOOLEAN NOT NULL DEFAULT FALSE,
    issued_at TIMESTAMPTZ NOT NULL,
    last_used_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ NOT NULL,
    idle_expires_at TIMESTAMPTZ NOT NULL,
    replaced_by UUID
);

CREATE TABLE IF NOT EXISTS data_requests (
    request_id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES user_accounts(user_id),
    type VARCHAR(20) NOT NULL,
    categories_csv VARCHAR(256) NOT NULL,
    delivery_channel VARCHAR(20),
    reason VARCHAR(500),
    status VARCHAR(20) NOT NULL,
    cooling_off_until TIMESTAMPTZ,
    due_by TIMESTAMPTZ NOT NULL,
    retained_categories_csv VARCHAR(512),
    completed_parts_csv VARCHAR(512),
    requested_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS auth_outbox_events (
    event_id UUID PRIMARY KEY,
    topic VARCHAR(128) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    payload_json TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS auth_idempotency_records (
    idempotency_key VARCHAR(128) NOT NULL,
    operation VARCHAR(64) NOT NULL,
    request_hash VARCHAR(128) NOT NULL,
    http_status INT NOT NULL,
    response_json TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (idempotency_key, operation)
);

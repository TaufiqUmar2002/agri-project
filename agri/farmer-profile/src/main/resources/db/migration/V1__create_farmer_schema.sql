CREATE TABLE IF NOT EXISTS eligibility_records (
    user_id UUID PRIMARY KEY,
    kyc_tier VARCHAR(16) NOT NULL,
    legal_name_token VARCHAR(128) NOT NULL,
    verified_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS farmer_profiles (
    farmer_id UUID PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE,
    status VARCHAR(24) NOT NULL,
    legal_name_token VARCHAR(128) NOT NULL,
    display_name VARCHAR(60),
    preferred_language VARCHAR(8) NOT NULL,
    preferred_channel VARCHAR(20) NOT NULL,
    quiet_start VARCHAR(8),
    quiet_end VARCHAR(8),
    quiet_timezone VARCHAR(64),
    country VARCHAR(8) NOT NULL,
    district VARCHAR(64) NOT NULL,
    ward VARCHAR(64) NOT NULL,
    village VARCHAR(80),
    farm_size_band VARCHAR(24) NOT NULL,
    primary_crops_csv VARCHAR(256) NOT NULL,
    attributes_json TEXT,
    enrolled_by_agent_id UUID,
    fraud_hold BOOLEAN NOT NULL DEFAULT FALSE,
    version INT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS farmer_wallets (
    wallet_id UUID PRIMARY KEY,
    farmer_id UUID NOT NULL REFERENCES farmer_profiles(farmer_id),
    provider VARCHAR(32) NOT NULL,
    wallet_number_token VARCHAR(128) NOT NULL,
    wallet_number_hash VARCHAR(128) NOT NULL,
    masked_number VARCHAR(32) NOT NULL,
    provider_name_token VARCHAR(128),
    match_band VARCHAR(16),
    status VARCHAR(32) NOT NULL,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    effective_from TIMESTAMPTZ NOT NULL,
    flags_csv VARCHAR(256),
    reason_code VARCHAR(64),
    reviewer_id VARCHAR(64),
    second_reviewer_id VARCHAR(64),
    reviewer_note VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL,
    retired_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS cooperative_groups (
    group_id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    group_type VARCHAR(32) NOT NULL,
    registration_number VARCHAR(40),
    country VARCHAR(8) NOT NULL,
    district VARCHAR(64) NOT NULL,
    leader_user_id UUID NOT NULL,
    sponsor_org_id VARCHAR(64),
    status VARCHAR(32) NOT NULL,
    max_members INT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS group_memberships (
    membership_id UUID PRIMARY KEY,
    group_id UUID NOT NULL REFERENCES cooperative_groups(group_id),
    farmer_id UUID,
    phone_hash VARCHAR(128),
    phone_token VARCHAR(128),
    status VARCHAR(24) NOT NULL,
    invited_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    confirmed_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS profile_histories (
    history_id UUID PRIMARY KEY,
    farmer_id UUID NOT NULL,
    version INT NOT NULL,
    changed_fields_csv VARCHAR(512) NOT NULL,
    diff_json TEXT NOT NULL,
    actor VARCHAR(64) NOT NULL,
    delegation_id VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS farmer_outbox_events (
    event_id UUID PRIMARY KEY,
    topic VARCHAR(128) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    payload_json TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS farmer_idempotency_records (
    idempotency_key VARCHAR(128) NOT NULL,
    operation VARCHAR(64) NOT NULL,
    request_hash VARCHAR(128) NOT NULL,
    http_status INT NOT NULL,
    response_json TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (idempotency_key, operation)
);

CREATE TABLE notifications (
    id UUID PRIMARY KEY,
    notification_id VARCHAR(64) NOT NULL UNIQUE,
    category VARCHAR(32) NOT NULL,
    template_code VARCHAR(80) NOT NULL,
    template_version INT NOT NULL,
    farmer_id VARCHAR(64),
    recipient_type VARCHAR(32) NOT NULL,
    recipient_ref VARCHAR(128) NOT NULL,
    msisdn_hash VARCHAR(128),
    language VARCHAR(16) NOT NULL,
    language_fallback BOOLEAN NOT NULL DEFAULT FALSE,
    channel_plan_json TEXT NOT NULL,
    current_channel VARCHAR(24),
    status VARCHAR(32) NOT NULL,
    deferred_until TIMESTAMP WITH TIME ZONE,
    suppression_reason VARCHAR(64),
    reason_code VARCHAR(64),
    variables_json TEXT,
    rendered_content TEXT,
    secret_token_ref VARCHAR(128),
    source_ref VARCHAR(128),
    source_event_id VARCHAR(128),
    dedupe_key VARCHAR(160),
    broadcast_id VARCHAR(64),
    failover_json TEXT,
    version INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    sent_at TIMESTAMP WITH TIME ZONE,
    delivered_at TIMESTAMP WITH TIME ZONE,
    expires_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_notif_dedupe ON notifications (dedupe_key, created_at);
CREATE INDEX idx_notif_farmer_status ON notifications (farmer_id, status, created_at DESC);

CREATE TABLE notification_attempts (
    id UUID PRIMARY KEY,
    attempt_id VARCHAR(64) NOT NULL UNIQUE,
    notification_id VARCHAR(64) NOT NULL,
    attempt_number INT NOT NULL,
    channel VARCHAR(24) NOT NULL,
    aggregator VARCHAR(64) NOT NULL,
    provider_message_id VARCHAR(128),
    status VARCHAR(32) NOT NULL,
    reason_code VARCHAR(64),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_attempt_notif ON notification_attempts (notification_id, attempt_number);
CREATE INDEX idx_attempt_provider_msg ON notification_attempts (provider_message_id);

CREATE TABLE notification_preferences (
    id UUID PRIMARY KEY,
    farmer_id VARCHAR(64) NOT NULL UNIQUE,
    language VARCHAR(16) NOT NULL,
    channels_json TEXT NOT NULL,
    whatsapp_opt_in BOOLEAN NOT NULL DEFAULT FALSE,
    opt_out_json TEXT NOT NULL,
    quiet_hours_json TEXT,
    consent_json TEXT,
    history_json TEXT,
    version INT NOT NULL DEFAULT 1,
    updated_by VARCHAR(64),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE suppression_list (
    id UUID PRIMARY KEY,
    msisdn_hash VARCHAR(128) NOT NULL,
    channel VARCHAR(24) NOT NULL,
    category VARCHAR(32) NOT NULL,
    reason VARCHAR(64) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_suppression_lookup ON suppression_list (msisdn_hash, active);

CREATE TABLE message_templates (
    id UUID PRIMARY KEY,
    code VARCHAR(80) NOT NULL,
    language VARCHAR(16) NOT NULL,
    version INT NOT NULL,
    status VARCHAR(32) NOT NULL,
    category VARCHAR(32) NOT NULL,
    mandatory BOOLEAN NOT NULL DEFAULT FALSE,
    accepts_secret BOOLEAN NOT NULL DEFAULT FALSE,
    variants_json TEXT NOT NULL,
    variables_json TEXT,
    checks_json TEXT,
    approvals_json TEXT,
    provider_status VARCHAR(32),
    current_version BOOLEAN NOT NULL DEFAULT FALSE,
    last_edited_by VARCHAR(64),
    approved_by VARCHAR(64),
    note VARCHAR(512),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_template_code_lang_ver UNIQUE (code, language, version)
);

CREATE TABLE broadcast_alerts (
    id UUID PRIMARY KEY,
    broadcast_id VARCHAR(64) NOT NULL UNIQUE,
    alert_type VARCHAR(64) NOT NULL,
    severity VARCHAR(24) NOT NULL,
    audience_json TEXT NOT NULL,
    template_code VARCHAR(80) NOT NULL,
    actions_json TEXT,
    variables_json TEXT,
    status VARCHAR(32) NOT NULL,
    target_count BIGINT NOT NULL DEFAULT 0,
    estimated_cost NUMERIC(18, 2) NOT NULL DEFAULT 0,
    proposal_hash VARCHAR(128),
    created_by VARCHAR(64),
    approved_by VARCHAR(64),
    schedule_at TIMESTAMP WITH TIME ZONE,
    expires_at TIMESTAMP WITH TIME ZONE,
    sent_count BIGINT NOT NULL DEFAULT 0,
    delivered_count BIGINT NOT NULL DEFAULT 0,
    failed_count BIGINT NOT NULL DEFAULT 0,
    suppressed_count BIGINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE ussd_sessions (
    id UUID PRIMARY KEY,
    session_id VARCHAR(128) NOT NULL UNIQUE,
    service_code VARCHAR(64) NOT NULL,
    msisdn_hash VARCHAR(128) NOT NULL,
    farmer_id VARCHAR(64),
    network_code VARCHAR(32),
    state VARCHAR(64) NOT NULL,
    menu_data_json TEXT,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE callback_inbox (
    id UUID PRIMARY KEY,
    nonce VARCHAR(128) NOT NULL UNIQUE,
    aggregator VARCHAR(64) NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL
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

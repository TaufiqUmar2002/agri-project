CREATE TABLE IF NOT EXISTS iot_devices (
    device_id VARCHAR(64) PRIMARY KEY,
    serial_number VARCHAR(64) NOT NULL UNIQUE,
    device_type VARCHAR(32) NOT NULL,
    manufacturer VARCHAR(64) NOT NULL,
    model VARCHAR(64) NOT NULL,
    protocol VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    connectivity VARCHAR(32) NOT NULL,
    plot_id VARCHAR(64),
    farmer_id VARCHAR(64),
    district_code VARCHAR(64),
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    accuracy_meters DOUBLE PRECISION,
    expected_interval_seconds INTEGER NOT NULL DEFAULT 900,
    installer_id VARCHAR(128) NOT NULL,
    consent_ref VARCHAR(64) NOT NULL,
    install_photo_token VARCHAR(128),
    calibration_version INTEGER NOT NULL DEFAULT 1,
    calibration_json TEXT NOT NULL,
    clock_offset_seconds INTEGER NOT NULL DEFAULT 0,
    last_seen_at TIMESTAMP WITH TIME ZONE,
    last_reading_at TIMESTAMP WITH TIME ZONE,
    battery_percent INTEGER,
    rssi INTEGER,
    alerts_csv TEXT NOT NULL DEFAULT '',
    credential_state VARCHAR(32) NOT NULL,
    cert_serial_masked VARCHAR(64),
    cert_expires_at TIMESTAMP WITH TIME ZONE,
    enrolment_token_hash VARCHAR(128),
    enrolment_expires_at TIMESTAMP WITH TIME ZONE,
    pending_proposal_hash VARCHAR(128),
    pending_proposal_actor VARCHAR(128),
    pending_proposal_json TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version_num INTEGER NOT NULL DEFAULT 1
);

CREATE INDEX IF NOT EXISTS idx_iot_plot ON iot_devices (plot_id);

CREATE TABLE IF NOT EXISTS iot_readings (
    ingest_id VARCHAR(128) PRIMARY KEY,
    device_id VARCHAR(64) NOT NULL,
    plot_id VARCHAR(64),
    district_code VARCHAR(64),
    channel VARCHAR(64) NOT NULL,
    raw_value DOUBLE PRECISION NOT NULL,
    metric_value DOUBLE PRECISION NOT NULL,
    unit VARCHAR(16) NOT NULL,
    observed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ingested_at TIMESTAMP WITH TIME ZONE NOT NULL,
    quality_flags_csv TEXT NOT NULL DEFAULT '',
    calibration_version INTEGER NOT NULL,
    revision INTEGER NOT NULL DEFAULT 0,
    late_arrival BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_iot_read_dev ON iot_readings (device_id, observed_at);

CREATE TABLE IF NOT EXISTS iot_rejected_readings (
    reject_id VARCHAR(64) PRIMARY KEY,
    device_id VARCHAR(64) NOT NULL,
    channel VARCHAR(64) NOT NULL,
    raw_value DOUBLE PRECISION,
    reason_code VARCHAR(64) NOT NULL,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS iot_outbox_events (
    event_id UUID PRIMARY KEY,
    topic VARCHAR(128) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    aggregate_key VARCHAR(128) NOT NULL,
    payload_json TEXT NOT NULL,
    published BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS iot_idempotency (
    idempotency_key VARCHAR(128) PRIMARY KEY,
    operation VARCHAR(64) NOT NULL,
    request_hash VARCHAR(128) NOT NULL,
    response_status INTEGER NOT NULL,
    response_body TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

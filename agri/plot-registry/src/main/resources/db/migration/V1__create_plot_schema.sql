CREATE TABLE IF NOT EXISTS plots (
    plot_id VARCHAR(64) PRIMARY KEY,
    farmer_id UUID NOT NULL,
    name VARCHAR(60),
    status VARCHAR(24) NOT NULL,
    capture_method VARCHAR(24) NOT NULL,
    accuracy_class VARCHAR(16) NOT NULL,
    geometry_geojson TEXT NOT NULL,
    geometry_version INT NOT NULL,
    geometry_hash VARCHAR(128) NOT NULL,
    area_ha DOUBLE PRECISION NOT NULL,
    min_width_m DOUBLE PRECISION NOT NULL,
    centroid_lat DOUBLE PRECISION NOT NULL,
    centroid_lon DOUBLE PRECISION NOT NULL,
    bbox VARCHAR(128) NOT NULL,
    geohash5 VARCHAR(10) NOT NULL,
    geohash7 VARCHAR(12) NOT NULL,
    country VARCHAR(8) NOT NULL,
    district VARCHAR(64) NOT NULL,
    ward VARCHAR(64) NOT NULL,
    flags_csv VARCHAR(512),
    coverage_status VARCHAR(24) NOT NULL,
    monitoring_status VARCHAR(24) NOT NULL,
    evidence_tokens_csv VARCHAR(1024),
    registered_by UUID,
    delegation_id UUID,
    device_id VARCHAR(128),
    attributes_json TEXT,
    reviewer_id VARCHAR(64),
    second_reviewer_id VARCHAR(64),
    second_review_pending BOOLEAN NOT NULL DEFAULT FALSE,
    reviewer_note VARCHAR(1000),
    version INT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    validated_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS plot_seasons (
    season_id VARCHAR(64) PRIMARY KEY,
    plot_id VARCHAR(64) NOT NULL REFERENCES plots(plot_id),
    season_label VARCHAR(32) NOT NULL,
    crop_code VARCHAR(32) NOT NULL,
    planting_date DATE NOT NULL,
    expected_harvest_date DATE NOT NULL,
    irrigation_type VARCHAR(24) NOT NULL,
    seed_variety VARCHAR(60),
    status VARCHAR(24) NOT NULL,
    locked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS plot_validations (
    validation_id UUID PRIMARY KEY,
    plot_id VARCHAR(64) NOT NULL REFERENCES plots(plot_id),
    geometry_version INT NOT NULL,
    rule_set_version VARCHAR(32) NOT NULL,
    outcome VARCHAR(24) NOT NULL,
    reason_codes_csv VARCHAR(512),
    metrics_json TEXT NOT NULL,
    dry_run BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS plot_coverage_windows (
    window_id UUID PRIMARY KEY,
    plot_id VARCHAR(64) NOT NULL REFERENCES plots(plot_id),
    policy_id VARCHAR(64) NOT NULL,
    valid_from TIMESTAMPTZ NOT NULL,
    valid_to TIMESTAMPTZ NOT NULL,
    status VARCHAR(24) NOT NULL
);

CREATE TABLE IF NOT EXISTS plot_outbox_events (
    event_id UUID PRIMARY KEY,
    topic VARCHAR(128) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    payload_json TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS plot_idempotency_records (
    idempotency_key VARCHAR(128) NOT NULL,
    operation VARCHAR(64) NOT NULL,
    request_hash VARCHAR(128) NOT NULL,
    http_status INT NOT NULL,
    response_json TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (idempotency_key, operation)
);

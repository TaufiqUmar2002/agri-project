-- Integration Hub & Extensible Hooks Microservice Schema (hub_db)

CREATE TABLE IF NOT EXISTS partners (
    partner_id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64),
    legal_name VARCHAR(200) NOT NULL,
    display_name VARCHAR(200) NOT NULL,
    type VARCHAR(32) NOT NULL,
    registration_number VARCHAR(100) NOT NULL UNIQUE,
    country_codes_json TEXT NOT NULL,
    contacts_json TEXT NOT NULL,
    allowed_event_types_json TEXT NOT NULL,
    data_scope VARCHAR(32) NOT NULL,
    data_scope_justification VARCHAR(500),
    visibility_json TEXT NOT NULL,
    connector_rights_json TEXT,
    extension_points_json TEXT,
    quotas_json TEXT NOT NULL,
    network_json TEXT,
    agreement_tokens_json TEXT NOT NULL,
    agreement_hashes_json TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    version INTEGER NOT NULL,
    partner_hash VARCHAR(128) NOT NULL,
    approval_id VARCHAR(64),
    oauth_client_id VARCHAR(128),
    mtls_profile_id VARCHAR(128),
    created_by VARCHAR(128) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS partner_versions (
    id VARCHAR(64) PRIMARY KEY,
    partner_id VARCHAR(64) NOT NULL,
    version INTEGER NOT NULL,
    partner_hash VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    snapshot_json TEXT NOT NULL,
    created_by VARCHAR(128) NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS webhook_subscriptions (
    subscription_id VARCHAR(64) PRIMARY KEY,
    partner_id VARCHAR(64) NOT NULL,
    name VARCHAR(80) NOT NULL,
    environment VARCHAR(24) NOT NULL,
    target_url VARCHAR(500) NOT NULL,
    event_types_json TEXT NOT NULL,
    filters_json TEXT,
    format VARCHAR(32) NOT NULL,
    data_scope VARCHAR(32) NOT NULL,
    ordering VARCHAR(16) NOT NULL,
    rate_limit_per_second INTEGER NOT NULL,
    batch_size INTEGER NOT NULL,
    contact_json TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    pause_reason VARCHAR(32),
    key_id VARCHAR(128) NOT NULL,
    key_created_at TIMESTAMP,
    previous_key_id VARCHAR(128),
    overlap_ends_at TIMESTAMP,
    challenge_result VARCHAR(32),
    challenge_checked_at TIMESTAMP,
    challenge_failure_reason VARCHAR(500),
    approval_id VARCHAR(64),
    security_blocked BOOLEAN NOT NULL DEFAULT FALSE,
    version INTEGER NOT NULL,
    created_by VARCHAR(128) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS webhook_deliveries (
    delivery_id VARCHAR(64) PRIMARY KEY,
    subscription_id VARCHAR(64) NOT NULL,
    partner_id VARCHAR(64) NOT NULL,
    event_id VARCHAR(128) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    subject_key VARCHAR(128),
    status VARCHAR(32) NOT NULL,
    attempts INTEGER NOT NULL,
    response_code INTEGER,
    latency_ms BIGINT,
    payload_bytes INTEGER,
    kafka_topic VARCHAR(128),
    kafka_partition INTEGER,
    kafka_offset BIGINT,
    reason_code VARCHAR(64),
    next_retry_at TIMESTAMP,
    replay BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS domain_event_archive (
    event_id VARCHAR(128) PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    kafka_topic VARCHAR(128) NOT NULL,
    kafka_partition INTEGER NOT NULL,
    kafka_offset BIGINT NOT NULL,
    subject_key VARCHAR(128),
    envelope_json TEXT NOT NULL,
    occurred_at TIMESTAMP NOT NULL,
    archived_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS replay_jobs (
    job_id VARCHAR(64) PRIMARY KEY,
    subscription_id VARCHAR(64) NOT NULL,
    partner_id VARCHAR(64) NOT NULL,
    mode VARCHAR(32) NOT NULL,
    target_delivery_id VARCHAR(64),
    from_time TIMESTAMP,
    to_time TIMESTAMP,
    from_partition INTEGER,
    from_offset BIGINT,
    to_offset BIGINT,
    event_types_json TEXT,
    include_delivered BOOLEAN NOT NULL DEFAULT FALSE,
    reason VARCHAR(300) NOT NULL,
    status VARCHAR(24) NOT NULL,
    estimated_events INTEGER NOT NULL,
    estimated_duration_seconds INTEGER NOT NULL,
    delivered_count INTEGER NOT NULL,
    failed_count INTEGER NOT NULL,
    skipped_count INTEGER NOT NULL,
    requested_by VARCHAR(128) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS tenant_map (
    subject_ref VARCHAR(128) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    group_id VARCHAR(64),
    source VARCHAR(64) NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS extension_points (
    hook_id VARCHAR(128) PRIMARY KEY,
    service_name VARCHAR(64) NOT NULL,
    service_version VARCHAR(32) NOT NULL,
    sdk_version VARCHAR(32) NOT NULL,
    type VARCHAR(24) NOT NULL,
    description VARCHAR(500) NOT NULL,
    current_contract_version VARCHAR(32) NOT NULL,
    previous_contract_version VARCHAR(32),
    deprecates_on VARCHAR(32),
    input_schema_ref VARCHAR(256),
    output_schema_ref VARCHAR(256),
    data_scopes_json TEXT,
    default_timeout_ms INTEGER NOT NULL,
    max_timeout_ms INTEGER NOT NULL,
    allowed_fail_modes_json TEXT NOT NULL,
    max_extensions_per_scope INTEGER NOT NULL,
    protected_point BOOLEAN NOT NULL,
    partner_visible BOOLEAN NOT NULL,
    rules_json TEXT,
    status VARCHAR(24) NOT NULL,
    last_registered_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS catalog_snapshots (
    id BIGINT PRIMARY KEY,
    snapshot_version BIGINT NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS extension_versions (
    id VARCHAR(96) PRIMARY KEY,
    extension_id VARCHAR(64) NOT NULL,
    name VARCHAR(80) NOT NULL,
    version VARCHAR(32) NOT NULL,
    extension_point VARCHAR(128) NOT NULL,
    owner_id VARCHAR(64) NOT NULL,
    contract_version VARCHAR(32) NOT NULL,
    runtime_type VARCHAR(24) NOT NULL,
    runtime_config_json TEXT NOT NULL,
    data_scope_json TEXT NOT NULL,
    timeout_ms INTEGER NOT NULL,
    memory_mb INTEGER NOT NULL,
    fail_mode VARCHAR(24) NOT NULL,
    secrets_json TEXT,
    test_vectors_json TEXT NOT NULL,
    description VARCHAR(1000) NOT NULL,
    changelog VARCHAR(1000) NOT NULL,
    artifact_hash VARCHAR(128) NOT NULL,
    artifact_token VARCHAR(128),
    scan_result VARCHAR(24) NOT NULL,
    scan_findings_json TEXT,
    tests_total INTEGER NOT NULL,
    tests_passed INTEGER NOT NULL,
    tests_failed INTEGER NOT NULL,
    deterministic BOOLEAN NOT NULL,
    max_latency_ms BIGINT NOT NULL,
    max_memory_mb INTEGER NOT NULL,
    status VARCHAR(24) NOT NULL,
    approval_id VARCHAR(64),
    etag_version INTEGER NOT NULL,
    created_by VARCHAR(128) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS extension_enablements (
    enablement_id VARCHAR(64) PRIMARY KEY,
    extension_id VARCHAR(64) NOT NULL,
    extension_version VARCHAR(32) NOT NULL,
    hook_id VARCHAR(128) NOT NULL,
    tenant_ids_json TEXT,
    product_ids_json TEXT,
    region_codes_json TEXT,
    priority INTEGER NOT NULL,
    fail_mode VARCHAR(24) NOT NULL,
    timeout_ms INTEGER NOT NULL,
    error_rate_percent INTEGER NOT NULL,
    window_minutes INTEGER NOT NULL,
    p95_latency_ms INTEGER,
    consecutive_failures INTEGER NOT NULL,
    active BOOLEAN NOT NULL,
    reason VARCHAR(500) NOT NULL,
    enabled_by VARCHAR(128) NOT NULL,
    effective_by TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS rollout_runs (
    rollout_id VARCHAR(64) PRIMARY KEY,
    extension_id VARCHAR(64) NOT NULL,
    extension_version VARCHAR(32) NOT NULL,
    hook_id VARCHAR(128) NOT NULL,
    stage VARCHAR(24) NOT NULL,
    status VARCHAR(24) NOT NULL,
    scope_json TEXT NOT NULL,
    canary_json TEXT,
    duration_hours INTEGER,
    criteria_json TEXT,
    invocations BIGINT NOT NULL,
    errors BIGINT NOT NULL,
    timeouts BIGINT NOT NULL,
    p50_ms BIGINT NOT NULL,
    p95_ms BIGINT NOT NULL,
    outcome_counts_json TEXT,
    agreement_percent DOUBLE PRECISION,
    unexpected_holds BIGINT,
    would_have_rejected BIGINT,
    sample_refs_json TEXT,
    verdict VARCHAR(32),
    reason VARCHAR(500) NOT NULL,
    started_by VARCHAR(128) NOT NULL,
    ends_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS invocation_records (
    invocation_id VARCHAR(64) PRIMARY KEY,
    hook_id VARCHAR(128) NOT NULL,
    extension_id VARCHAR(64) NOT NULL,
    extension_version VARCHAR(32) NOT NULL,
    caller_service VARCHAR(64) NOT NULL,
    tenant_id VARCHAR(64),
    subject_ref_hash VARCHAR(128) NOT NULL,
    result VARCHAR(32) NOT NULL,
    fail_mode_applied VARCHAR(32) NOT NULL,
    latency_ms BIGINT NOT NULL,
    input_hash VARCHAR(128) NOT NULL,
    output_hash VARCHAR(128) NOT NULL,
    shadow_mode BOOLEAN NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS connectors (
    connector_id VARCHAR(64) PRIMARY KEY,
    owner_id VARCHAR(64) NOT NULL,
    name VARCHAR(80) NOT NULL,
    direction VARCHAR(16) NOT NULL,
    adapter_type VARCHAR(64) NOT NULL,
    adapter_custom_ref VARCHAR(128),
    endpoint_json TEXT,
    auth_type VARCHAR(24) NOT NULL,
    auth_json TEXT NOT NULL,
    external_schema_json TEXT NOT NULL,
    mapping_json TEXT NOT NULL,
    canonical_target_json TEXT NOT NULL,
    pii_fields_json TEXT,
    quotas_json TEXT NOT NULL,
    environment VARCHAR(24) NOT NULL,
    status VARCHAR(24) NOT NULL,
    config_hash VARCHAR(128) NOT NULL,
    records_tested INTEGER NOT NULL,
    mapped_count INTEGER NOT NULL,
    rejected_count INTEGER NOT NULL,
    rejection_samples_json TEXT,
    approval_id VARCHAR(64),
    version INTEGER NOT NULL,
    created_by VARCHAR(128) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS inbound_receipts (
    receipt_id VARCHAR(64) PRIMARY KEY,
    connector_id VARCHAR(64) NOT NULL,
    owner_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    source_id VARCHAR(100),
    status VARCHAR(32) NOT NULL,
    received_count INTEGER NOT NULL,
    accepted_count INTEGER NOT NULL,
    duplicate_count INTEGER NOT NULL,
    rejected_count INTEGER NOT NULL,
    outcomes_json TEXT,
    received_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS inbound_record_dedup (
    id VARCHAR(96) PRIMARY KEY,
    connector_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    external_id VARCHAR(120) NOT NULL,
    receipt_id VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_inbound_dedup UNIQUE (connector_id, event_type, external_id)
);

CREATE TABLE IF NOT EXISTS approvals (
    approval_id VARCHAR(64) PRIMARY KEY,
    type VARCHAR(32) NOT NULL,
    subject_id VARCHAR(64) NOT NULL,
    subject_version INTEGER NOT NULL,
    subject_hash VARCHAR(128) NOT NULL,
    required_roles_json TEXT NOT NULL,
    decisions_json TEXT NOT NULL,
    status VARCHAR(24) NOT NULL,
    requested_by VARCHAR(128) NOT NULL,
    sla_due_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS idempotency_records (
    id VARCHAR(64) PRIMARY KEY,
    operation VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    actor_id VARCHAR(128) NOT NULL,
    request_hash VARCHAR(128) NOT NULL,
    response_status INTEGER NOT NULL,
    response_body_json TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_idemp_op_key UNIQUE (operation, idempotency_key)
);

CREATE TABLE IF NOT EXISTS outbox_events (
    id VARCHAR(64) PRIMARY KEY,
    topic VARCHAR(128) NOT NULL,
    event_key VARCHAR(128) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload_json TEXT NOT NULL,
    published BOOLEAN NOT NULL DEFAULT FALSE,
    published_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS subscription_test_records (
    test_id VARCHAR(64) PRIMARY KEY,
    subscription_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    result VARCHAR(32) NOT NULL,
    response_code INTEGER,
    latency_ms BIGINT,
    response_snippet VARCHAR(2048),
    tested_at TIMESTAMP NOT NULL
);

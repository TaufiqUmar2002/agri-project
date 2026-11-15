INSERT INTO retention_class (
    id, name, retention_group,
    period_days, minimum_days,
    legal_basis, status
)
VALUES
(
    '10000000-0000-0000-0000-000000000001',
    'Erasable Data',
    'ERASABLE',
    NULL, NULL,
    'Subject to applicable erasure requirements',
    'ACTIVE'
),
(
    '10000000-0000-0000-0000-000000000002',
    'KYC Retention',
    'KYC_RETAIN',
    NULL, NULL,
    'KYC retention requirements - legal confirmation pending',
    'ACTIVE'
),
(
    '10000000-0000-0000-0000-000000000003',
    'Financial Retention',
    'FINANCIAL_RETAIN',
    NULL, NULL,
    'Financial retention requirements - legal confirmation pending',
    'ACTIVE'
),
(
    '10000000-0000-0000-0000-000000000004',
    'Evidence Retention',
    'EVIDENCE_RETAIN',
    NULL, NULL,
    'Evidence retention requirements - legal confirmation pending',
    'ACTIVE'
);

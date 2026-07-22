CREATE TABLE disclosure_processing_job (
    job_id VARCHAR(64) PRIMARY KEY,
    partner_id VARCHAR(80) NOT NULL,
    stock_code VARCHAR(6) NOT NULL,
    receipt_number VARCHAR(20) NOT NULL,
    corporation_name VARCHAR(200) NOT NULL,
    report_name VARCHAR(500) NOT NULL,
    original_url VARCHAR(1000) NOT NULL,
    published_at TIMESTAMP NOT NULL,
    source_content TEXT,
    content_hash VARCHAR(128),
    source_license_policy VARCHAR(200),
    status VARCHAR(20) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP NOT NULL,
    lease_token VARCHAR(64),
    lease_until TIMESTAMP,
    alert_id VARCHAR(80),
    last_error VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_disclosure_processing_source
        UNIQUE (partner_id, stock_code, original_url),
    CONSTRAINT ck_disclosure_processing_status
        CHECK (status IN ('PENDING', 'PROCESSING', 'RETRY', 'READY', 'REJECTED'))
);

CREATE INDEX idx_disclosure_processing_claim
    ON disclosure_processing_job (status, next_attempt_at, attempt_count, published_at DESC);

CREATE INDEX idx_disclosure_processing_stock
    ON disclosure_processing_job (partner_id, stock_code, status, published_at DESC);

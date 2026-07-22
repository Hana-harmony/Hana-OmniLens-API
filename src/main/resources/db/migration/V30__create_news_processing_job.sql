CREATE TABLE news_processing_job (
    job_id VARCHAR(64) PRIMARY KEY,
    partner_id VARCHAR(80) NOT NULL,
    stock_code VARCHAR(6) NOT NULL,
    title VARCHAR(1000) NOT NULL,
    snippet TEXT,
    original_url VARCHAR(1000) NOT NULL,
    published_at TIMESTAMP NOT NULL,
    source_content TEXT NOT NULL,
    image_urls TEXT,
    canonical_url VARCHAR(1000),
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
    CONSTRAINT uk_news_processing_source
        UNIQUE (partner_id, stock_code, original_url),
    CONSTRAINT ck_news_processing_status
        CHECK (status IN ('PENDING', 'PROCESSING', 'RETRY', 'READY', 'REJECTED'))
);

CREATE INDEX idx_news_processing_claim
    ON news_processing_job (status, next_attempt_at, attempt_count, published_at DESC);

CREATE INDEX idx_news_processing_stock
    ON news_processing_job (partner_id, stock_code, status, published_at DESC);

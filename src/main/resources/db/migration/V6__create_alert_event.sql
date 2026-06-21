CREATE TABLE IF NOT EXISTS alert_event (
    alert_id VARCHAR(80) PRIMARY KEY,
    partner_id VARCHAR(80) NOT NULL,
    stock_code VARCHAR(6) NOT NULL,
    source_type VARCHAR(20) NOT NULL,
    original_url VARCHAR(1000) NOT NULL,
    duplicate_key VARCHAR(128),
    cluster_key VARCHAR(128),
    content_availability VARCHAR(40) NOT NULL,
    published_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    event_json TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_alert_event_stock_published
    ON alert_event (stock_code, published_at DESC);

CREATE INDEX IF NOT EXISTS idx_alert_event_partner_published
    ON alert_event (partner_id, published_at DESC);

CREATE INDEX IF NOT EXISTS idx_alert_event_duplicate
    ON alert_event (source_type, duplicate_key);

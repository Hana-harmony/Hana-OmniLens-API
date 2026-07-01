CREATE TABLE IF NOT EXISTS market_news_event (
    news_id VARCHAR(80) PRIMARY KEY,
    query VARCHAR(120) NOT NULL,
    original_url VARCHAR(1000) NOT NULL,
    duplicate_key VARCHAR(128) NOT NULL,
    published_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    event_json TEXT NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_market_news_event_duplicate
    ON market_news_event (duplicate_key);

CREATE INDEX IF NOT EXISTS idx_market_news_event_published
    ON market_news_event (published_at DESC, created_at DESC);

CREATE TABLE IF NOT EXISTS korean_financial_term_explanation_cache (
    cache_key VARCHAR(128) PRIMARY KEY,
    term VARCHAR(80) NOT NULL,
    normalized_term VARCHAR(80) NOT NULL,
    locale VARCHAR(10) NOT NULL,
    article_id VARCHAR(120) NOT NULL,
    stock_code VARCHAR(6),
    source VARCHAR(40) NOT NULL,
    display_mode VARCHAR(40) NOT NULL,
    cacheable BOOLEAN NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    response_json TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_kft_explanation_cache_term
    ON korean_financial_term_explanation_cache (normalized_term, locale, expires_at);

CREATE TABLE IF NOT EXISTS korean_financial_term_click_log (
    click_id VARCHAR(80) PRIMARY KEY,
    occurred_at TIMESTAMP NOT NULL,
    term VARCHAR(80) NOT NULL,
    normalized_term VARCHAR(80) NOT NULL,
    locale VARCHAR(10) NOT NULL,
    source_type VARCHAR(20) NOT NULL,
    article_id VARCHAR(120) NOT NULL,
    article_url VARCHAR(1000) NOT NULL,
    stock_code VARCHAR(6),
    stock_name VARCHAR(80) NOT NULL,
    user_hash VARCHAR(64) NOT NULL,
    session_hash VARCHAR(64) NOT NULL,
    cache_hit BOOLEAN NOT NULL,
    explanation_source VARCHAR(40) NOT NULL,
    display_mode VARCHAR(40) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_kft_click_log_term
    ON korean_financial_term_click_log (normalized_term, locale, occurred_at DESC);

CREATE INDEX IF NOT EXISTS idx_kft_click_log_article
    ON korean_financial_term_click_log (article_id, occurred_at DESC);

CREATE TABLE IF NOT EXISTS korean_financial_term_click_stats (
    normalized_term VARCHAR(80) NOT NULL,
    locale VARCHAR(10) NOT NULL,
    click_count BIGINT NOT NULL,
    cache_hit_count BIGINT NOT NULL,
    review_required_count BIGINT NOT NULL,
    first_clicked_at TIMESTAMP NOT NULL,
    last_clicked_at TIMESTAMP NOT NULL,
    PRIMARY KEY (normalized_term, locale)
);

CREATE INDEX IF NOT EXISTS idx_kft_click_stats_count
    ON korean_financial_term_click_stats (click_count DESC, last_clicked_at DESC);

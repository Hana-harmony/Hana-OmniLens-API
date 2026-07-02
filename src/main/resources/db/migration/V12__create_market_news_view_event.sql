CREATE TABLE IF NOT EXISTS market_news_view_event (
    view_id BIGSERIAL PRIMARY KEY,
    news_id VARCHAR(80) NOT NULL REFERENCES market_news_event(news_id) ON DELETE CASCADE,
    viewed_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_market_news_view_event_window
    ON market_news_view_event (viewed_at DESC, news_id);

CREATE INDEX IF NOT EXISTS idx_market_news_view_event_news
    ON market_news_view_event (news_id, viewed_at DESC);

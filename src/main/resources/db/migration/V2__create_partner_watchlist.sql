CREATE TABLE IF NOT EXISTS partner_watchlist_subscription (
    partner_id VARCHAR(80) NOT NULL,
    stock_code VARCHAR(6) NOT NULL REFERENCES stock_master (stock_code),
    sort_order INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (partner_id, stock_code)
);

CREATE INDEX IF NOT EXISTS ix_partner_watchlist_stock_code
    ON partner_watchlist_subscription (stock_code);

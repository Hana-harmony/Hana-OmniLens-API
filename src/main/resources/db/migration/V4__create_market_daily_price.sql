CREATE TABLE IF NOT EXISTS market_daily_price (
    stock_code VARCHAR(6) NOT NULL REFERENCES stock_master (stock_code),
    trade_date DATE NOT NULL,
    market VARCHAR(20) NOT NULL,
    open_price_krw NUMERIC(19, 4) NOT NULL,
    high_price_krw NUMERIC(19, 4) NOT NULL,
    low_price_krw NUMERIC(19, 4) NOT NULL,
    close_price_krw NUMERIC(19, 4) NOT NULL,
    change_rate NUMERIC(9, 4) NOT NULL,
    trading_volume BIGINT NOT NULL,
    trading_value_krw NUMERIC(24, 4) NOT NULL,
    adjusted_close_price_krw NUMERIC(19, 4) NOT NULL,
    source VARCHAR(80) NOT NULL,
    collected_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (stock_code, trade_date)
);

CREATE INDEX IF NOT EXISTS ix_market_daily_price_trade_date
    ON market_daily_price (trade_date);

CREATE INDEX IF NOT EXISTS ix_market_daily_price_stock_date
    ON market_daily_price (stock_code, trade_date DESC);

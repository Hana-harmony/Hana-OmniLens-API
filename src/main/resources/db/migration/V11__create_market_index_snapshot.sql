CREATE TABLE IF NOT EXISTS market_index_latest_snapshot (
    index_code VARCHAR(10) PRIMARY KEY,
    index_name VARCHAR(80) NOT NULL,
    market VARCHAR(20) NOT NULL,
    current_value NUMERIC(19, 4) NOT NULL,
    change_sign VARCHAR(8) NOT NULL,
    change_value NUMERIC(19, 4) NOT NULL,
    change_rate NUMERIC(12, 6) NOT NULL,
    accumulated_volume BIGINT NOT NULL,
    accumulated_trading_value BIGINT NOT NULL,
    open_value NUMERIC(19, 4) NOT NULL,
    high_value NUMERIC(19, 4) NOT NULL,
    low_value NUMERIC(19, 4) NOT NULL,
    market_data_time TIMESTAMP NOT NULL,
    trade_date DATE NOT NULL,
    source VARCHAR(80) NOT NULL,
    collected_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS ix_market_index_latest_snapshot_trade_date
    ON market_index_latest_snapshot (trade_date, index_code);

CREATE TABLE IF NOT EXISTS market_index_intraday_minute_price (
    index_code VARCHAR(10) NOT NULL,
    index_name VARCHAR(80) NOT NULL,
    market VARCHAR(20) NOT NULL,
    bucket_start TIMESTAMP NOT NULL,
    trade_date DATE NOT NULL,
    open_value NUMERIC(19, 4) NOT NULL,
    high_value NUMERIC(19, 4) NOT NULL,
    low_value NUMERIC(19, 4) NOT NULL,
    close_value NUMERIC(19, 4) NOT NULL,
    trading_volume BIGINT NOT NULL,
    trading_value_krw NUMERIC(24, 4) NOT NULL,
    source VARCHAR(80) NOT NULL,
    collected_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (index_code, bucket_start)
);

CREATE INDEX IF NOT EXISTS ix_market_index_intraday_price_index_date
    ON market_index_intraday_minute_price (index_code, trade_date, bucket_start ASC);

CREATE INDEX IF NOT EXISTS ix_market_index_intraday_price_trade_date
    ON market_index_intraday_minute_price (trade_date);

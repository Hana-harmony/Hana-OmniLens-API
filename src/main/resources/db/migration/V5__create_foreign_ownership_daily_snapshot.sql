CREATE TABLE IF NOT EXISTS foreign_ownership_daily_snapshot (
    stock_code VARCHAR(6) NOT NULL REFERENCES stock_master (stock_code),
    base_date DATE NOT NULL,
    foreign_owned_quantity BIGINT NOT NULL,
    foreign_ownership_rate NUMERIC(9, 4) NOT NULL,
    foreign_limit_quantity BIGINT NOT NULL,
    foreign_limit_exhaustion_rate NUMERIC(9, 4) NOT NULL,
    source VARCHAR(80) NOT NULL,
    collected_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (stock_code, base_date)
);

CREATE INDEX IF NOT EXISTS ix_foreign_ownership_daily_snapshot_stock_date
    ON foreign_ownership_daily_snapshot (stock_code, base_date DESC);

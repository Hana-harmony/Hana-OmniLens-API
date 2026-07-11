ALTER TABLE stock_master
    ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE stock_master
    ADD COLUMN IF NOT EXISTS master_synced_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_stock_master_active_market
    ON stock_master (active, market, stock_code);

CREATE TABLE IF NOT EXISTS stock_master (
    stock_code VARCHAR(6) PRIMARY KEY,
    stock_name VARCHAR(120) NOT NULL,
    stock_name_en VARCHAR(160) NOT NULL,
    market VARCHAR(20) NOT NULL,
    isin_code VARCHAR(12) NOT NULL,
    dart_corp_code VARCHAR(8) NOT NULL DEFAULT '',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_stock_master_isin_code
    ON stock_master (isin_code);

CREATE INDEX IF NOT EXISTS ix_stock_master_stock_name
    ON stock_master (stock_name);

CREATE INDEX IF NOT EXISTS ix_stock_master_stock_name_en
    ON stock_master (stock_name_en);

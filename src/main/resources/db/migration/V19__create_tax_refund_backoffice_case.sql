CREATE TABLE IF NOT EXISTS tax_refund_backoffice_cases (
    case_id VARCHAR(32) PRIMARY KEY,
    account_id VARCHAR(32) NOT NULL,
    user_id VARCHAR(32) NOT NULL,
    tax_year INTEGER NOT NULL,
    treaty_country CHAR(2) NOT NULL,
    estimated_refund_usd NUMERIC(18, 2) NOT NULL,
    advance_payment_requested BOOLEAN NOT NULL,
    advance_payment_eligible BOOLEAN NOT NULL,
    matched_trade_ids_json TEXT NOT NULL,
    verified_documents_json TEXT NOT NULL DEFAULT '[]',
    status VARCHAR(40) NOT NULL,
    requested_at TIMESTAMP NOT NULL,
    synced_at TIMESTAMP NOT NULL,
    tax_office_submission_status VARCHAR(40) NOT NULL DEFAULT 'NOT_SUBMITTED',
    tax_office_submitted_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS ix_tax_refund_backoffice_cases_status
    ON tax_refund_backoffice_cases (tax_office_submission_status, synced_at DESC);

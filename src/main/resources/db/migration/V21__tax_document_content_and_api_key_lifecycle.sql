CREATE TABLE IF NOT EXISTS tax_refund_backoffice_documents (
    case_id VARCHAR(32) NOT NULL REFERENCES tax_refund_backoffice_cases(case_id) ON DELETE CASCADE,
    document_id VARCHAR(40) NOT NULL,
    document_type VARCHAR(40) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(40) NOT NULL,
    content_sha256 CHAR(64) NOT NULL,
    content_bytes BYTEA NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (case_id, document_id)
);

CREATE INDEX IF NOT EXISTS ix_tax_refund_backoffice_documents_case
    ON tax_refund_backoffice_documents (case_id, document_type);

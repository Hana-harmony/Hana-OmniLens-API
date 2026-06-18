CREATE TABLE IF NOT EXISTS partner_api_credential (
    api_key_sha256 VARCHAR(64) PRIMARY KEY,
    partner_id VARCHAR(80) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS ix_partner_api_credential_partner_id
    ON partner_api_credential (partner_id);

CREATE INDEX IF NOT EXISTS ix_partner_api_credential_active
    ON partner_api_credential (active);

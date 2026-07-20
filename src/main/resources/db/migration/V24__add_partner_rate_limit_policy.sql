ALTER TABLE partner_api_credential
    ADD COLUMN IF NOT EXISTS rate_limit_exempt BOOLEAN NOT NULL DEFAULT FALSE;

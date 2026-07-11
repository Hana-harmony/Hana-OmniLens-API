CREATE TABLE IF NOT EXISTS portal_users (
    user_id VARCHAR(48) PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    phone_number VARCHAR(30) NOT NULL,
    role VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS partner_api_key_applications (
    application_id VARCHAR(48) PRIMARY KEY,
    user_id VARCHAR(48) NOT NULL REFERENCES portal_users(user_id),
    partner_id VARCHAR(96) NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL,
    requested_at TIMESTAMP NOT NULL,
    reviewed_at TIMESTAMP,
    reviewed_by_user_id VARCHAR(48),
    encrypted_api_key TEXT,
    api_key_sha256_prefix VARCHAR(12),
    rejection_reason VARCHAR(500),
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS ix_partner_api_key_application_status
    ON partner_api_key_applications (status, requested_at DESC);

CREATE INDEX IF NOT EXISTS ix_partner_api_key_application_user
    ON partner_api_key_applications (user_id, requested_at DESC);

ALTER TABLE portal_users ADD COLUMN IF NOT EXISTS password_change_required BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE portal_users ADD COLUMN IF NOT EXISTS session_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE portal_users ADD COLUMN IF NOT EXISTS password_changed_at TIMESTAMP;

UPDATE portal_users
SET password_hash = '{bcrypt}' || password_hash
WHERE password_hash LIKE '$2%';

INSERT INTO portal_users (
    user_id, username, password_hash, display_name, phone_number, role,
    created_at, updated_at, password_change_required, session_version
)
SELECT
    'PUSR-ADMIN000000000000000',
    'admin',
    '{bcrypt}$2y$12$QYdm5Z2QBMF/9XgtNMvA5umnErMvlTRskDzg4U5wcIN5PH.X9Sf/K',
    'Hana Omni-Connect Admin',
    '',
    'ADMIN',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    TRUE,
    0
WHERE NOT EXISTS (SELECT 1 FROM portal_users WHERE username = 'admin');

ALTER TABLE tax_refund_backoffice_cases ADD COLUMN IF NOT EXISTS correction_fields_json TEXT NOT NULL DEFAULT '{}';
ALTER TABLE tax_refund_backoffice_cases ADD COLUMN IF NOT EXISTS correction_request_status VARCHAR(40) NOT NULL DEFAULT 'NOT_PREPARED';
ALTER TABLE tax_refund_backoffice_cases ADD COLUMN IF NOT EXISTS correction_pdf BYTEA;
ALTER TABLE tax_refund_backoffice_cases ADD COLUMN IF NOT EXISTS correction_pdf_sha256 VARCHAR(64);
ALTER TABLE tax_refund_backoffice_cases ADD COLUMN IF NOT EXISTS correction_prepared_at TIMESTAMP;
ALTER TABLE tax_refund_backoffice_cases ADD COLUMN IF NOT EXISTS correction_prepared_by_user_id VARCHAR(48);
ALTER TABLE tax_refund_backoffice_cases ADD COLUMN IF NOT EXISTS approved_at TIMESTAMP;
ALTER TABLE tax_refund_backoffice_cases ADD COLUMN IF NOT EXISTS approved_by_user_id VARCHAR(48);

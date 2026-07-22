CREATE TABLE portal_sessions (
    session_id VARCHAR(40) PRIMARY KEY,
    user_id VARCHAR(25) NOT NULL REFERENCES portal_users(user_id) ON DELETE CASCADE,
    session_version BIGINT NOT NULL,
    issued_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_portal_sessions_active_user
    ON portal_sessions(user_id, revoked_at, expires_at DESC);

CREATE INDEX idx_portal_sessions_expiry
    ON portal_sessions(expires_at);

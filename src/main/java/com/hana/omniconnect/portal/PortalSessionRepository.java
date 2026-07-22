package com.hana.omniconnect.portal;

import java.sql.Timestamp;
import java.time.Instant;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PortalSessionRepository {

    private final JdbcTemplate jdbcTemplate;

    public PortalSessionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void create(
            String sessionId,
            String userId,
            long sessionVersion,
            Instant issuedAt,
            Instant expiresAt) {
        jdbcTemplate.update(
                "INSERT INTO portal_sessions (session_id, user_id, session_version, issued_at, expires_at) "
                        + "VALUES (?, ?, ?, ?, ?)",
                sessionId,
                userId,
                sessionVersion,
                Timestamp.from(issuedAt),
                Timestamp.from(expiresAt));
    }

    public boolean isActive(String sessionId, String userId, long sessionVersion, Instant now) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM portal_sessions "
                        + "WHERE session_id = ? AND user_id = ? AND session_version = ? "
                        + "AND revoked_at IS NULL AND expires_at > ?",
                Integer.class,
                sessionId,
                userId,
                sessionVersion,
                Timestamp.from(now));
        return count != null && count == 1;
    }

    public void revoke(String sessionId, String userId, Instant revokedAt) {
        jdbcTemplate.update(
                "UPDATE portal_sessions SET revoked_at = ? "
                        + "WHERE session_id = ? AND user_id = ? AND revoked_at IS NULL",
                Timestamp.from(revokedAt),
                sessionId,
                userId);
    }

    public int deleteExpiredBefore(Instant cutoff) {
        return jdbcTemplate.update(
                "DELETE FROM portal_sessions WHERE expires_at < ? OR revoked_at < ?",
                Timestamp.from(cutoff),
                Timestamp.from(cutoff));
    }
}

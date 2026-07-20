package com.hana.omniconnect.portal;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PortalUserRepository {

    private final JdbcTemplate jdbcTemplate;

    public PortalUserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<PortalUser> findByUsername(String username) {
        return jdbcTemplate.query(select() + " WHERE username = ?", (resultSet, rowNumber) -> user(resultSet), username)
                .stream()
                .findFirst();
    }

    public Optional<PortalUser> findByUserId(String userId) {
        return jdbcTemplate.query(select() + " WHERE user_id = ?", (resultSet, rowNumber) -> user(resultSet), userId)
                .stream()
                .findFirst();
    }

    public void save(PortalUser user) {
        jdbcTemplate.update(
                "INSERT INTO portal_users (user_id, username, password_hash, display_name, phone_number, role, created_at, updated_at, password_change_required, session_version, password_changed_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                user.userId(), user.username(), user.passwordHash(), user.displayName(), user.phoneNumber(), user.role().name(),
                Timestamp.from(user.createdAt()), Timestamp.from(user.updatedAt()), user.passwordChangeRequired(), user.sessionVersion(),
                user.passwordChangedAt() == null ? null : Timestamp.from(user.passwordChangedAt()));
    }

    public void updatePassword(String userId, String passwordHash, Instant changedAt) {
        jdbcTemplate.update(
                "UPDATE portal_users SET password_hash = ?, password_change_required = FALSE, session_version = session_version + 1, password_changed_at = ?, updated_at = ? WHERE user_id = ?",
                passwordHash, Timestamp.from(changedAt), Timestamp.from(changedAt), userId);
    }

    public void upgradePasswordHash(String userId, String passwordHash, Instant updatedAt) {
        jdbcTemplate.update(
                "UPDATE portal_users SET password_hash = ?, session_version = session_version + 1, updated_at = ? WHERE user_id = ?",
                passwordHash, Timestamp.from(updatedAt), userId);
    }

    public void revokeSessions(String userId, Instant changedAt) {
        jdbcTemplate.update(
                "UPDATE portal_users SET session_version = session_version + 1, updated_at = ? WHERE user_id = ?",
                Timestamp.from(changedAt), userId);
    }

    private String select() {
        return "SELECT user_id, username, password_hash, display_name, phone_number, role, created_at, updated_at, password_change_required, session_version, password_changed_at FROM portal_users";
    }

    private PortalUser user(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        return new PortalUser(
                resultSet.getString("user_id"),
                resultSet.getString("username"),
                resultSet.getString("password_hash"),
                resultSet.getString("display_name"),
                resultSet.getString("phone_number"),
                PortalRole.valueOf(resultSet.getString("role")),
                instant(resultSet, "created_at"),
                instant(resultSet, "updated_at"),
                resultSet.getBoolean("password_change_required"),
                resultSet.getLong("session_version"),
                nullableInstant(resultSet, "password_changed_at"));
    }

    private Instant instant(java.sql.ResultSet resultSet, String column) throws java.sql.SQLException {
        return resultSet.getTimestamp(column).toInstant();
    }

    private Instant nullableInstant(java.sql.ResultSet resultSet, String column) throws java.sql.SQLException {
        Timestamp value = resultSet.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}

package com.hana.omniconnect.portal;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PortalApiKeyApplicationRepository {

    private final JdbcTemplate jdbcTemplate;

    public PortalApiKeyApplicationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void save(PortalApiKeyApplication application) {
        jdbcTemplate.update(
                "INSERT INTO partner_api_key_applications (application_id, user_id, partner_id, status, requested_at, reviewed_at, reviewed_by_user_id, encrypted_api_key, api_key_sha256_prefix, rejection_reason, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                application.applicationId(), application.userId(), application.partnerId(), application.status().name(),
                Timestamp.from(application.requestedAt()), timestamp(application.reviewedAt()), application.reviewedByUserId(),
                application.encryptedApiKey(), application.apiKeySha256Prefix(), application.rejectionReason(),
                Timestamp.from(application.updatedAt()));
    }

    public Optional<PortalApiKeyApplication> findById(String applicationId) {
        return jdbcTemplate.query(select() + " WHERE application_id = ?", (resultSet, rowNumber) -> application(resultSet), applicationId)
                .stream()
                .findFirst();
    }

    public Optional<PortalApiKeyApplication> findByIdForUpdate(String applicationId) {
        return jdbcTemplate.query(
                        select() + " WHERE application_id = ? FOR UPDATE",
                        (resultSet, rowNumber) -> application(resultSet),
                        applicationId)
                .stream()
                .findFirst();
    }

    public void clearEncryptedApiKey(String applicationId, Instant updatedAt) {
        jdbcTemplate.update(
                "UPDATE partner_api_key_applications SET encrypted_api_key = NULL, updated_at = ? WHERE application_id = ?",
                Timestamp.from(updatedAt), applicationId);
    }

    public List<PortalApiKeyApplication> findByUserId(String userId) {
        return jdbcTemplate.query(select() + " WHERE user_id = ? ORDER BY requested_at DESC", (resultSet, rowNumber) -> application(resultSet), userId);
    }

    public List<PortalApiKeyApplication> findAll() {
        return jdbcTemplate.query(select() + " ORDER BY requested_at DESC", (resultSet, rowNumber) -> application(resultSet));
    }

    public void update(PortalApiKeyApplication application) {
        jdbcTemplate.update(
                "UPDATE partner_api_key_applications SET status = ?, reviewed_at = ?, reviewed_by_user_id = ?, encrypted_api_key = ?, api_key_sha256_prefix = ?, rejection_reason = ?, updated_at = ? WHERE application_id = ?",
                application.status().name(), timestamp(application.reviewedAt()), application.reviewedByUserId(),
                application.encryptedApiKey(), application.apiKeySha256Prefix(), application.rejectionReason(),
                Timestamp.from(application.updatedAt()), application.applicationId());
    }

    public void resubmit(PortalApiKeyApplication application) {
        jdbcTemplate.update(
                "UPDATE partner_api_key_applications SET status = ?, requested_at = ?, reviewed_at = NULL, reviewed_by_user_id = NULL, encrypted_api_key = NULL, api_key_sha256_prefix = NULL, rejection_reason = NULL, updated_at = ? WHERE application_id = ?",
                application.status().name(), Timestamp.from(application.requestedAt()),
                Timestamp.from(application.updatedAt()), application.applicationId());
    }

    private String select() {
        return "SELECT application_id, user_id, partner_id, status, requested_at, reviewed_at, reviewed_by_user_id, encrypted_api_key, api_key_sha256_prefix, rejection_reason, updated_at FROM partner_api_key_applications";
    }

    private PortalApiKeyApplication application(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        return new PortalApiKeyApplication(
                resultSet.getString("application_id"), resultSet.getString("user_id"), resultSet.getString("partner_id"),
                ApiKeyApplicationStatus.valueOf(resultSet.getString("status")), instant(resultSet, "requested_at"),
                nullableInstant(resultSet, "reviewed_at"), resultSet.getString("reviewed_by_user_id"),
                resultSet.getString("encrypted_api_key"), resultSet.getString("api_key_sha256_prefix"),
                resultSet.getString("rejection_reason"), instant(resultSet, "updated_at"));
    }

    private Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private Instant instant(java.sql.ResultSet resultSet, String column) throws java.sql.SQLException {
        return resultSet.getTimestamp(column).toInstant();
    }

    private Instant nullableInstant(java.sql.ResultSet resultSet, String column) throws java.sql.SQLException {
        Timestamp timestamp = resultSet.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }
}

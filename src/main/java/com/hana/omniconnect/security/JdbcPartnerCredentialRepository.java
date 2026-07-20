package com.hana.omniconnect.security;

import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcPartnerCredentialRepository implements PartnerCredentialRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcPartnerCredentialRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<PartnerCredential> findActiveByApiKeySha256(String apiKeySha256) {
        return jdbcTemplate.query(
                        """
                        SELECT partner_id, api_key_sha256, rate_limit_exempt
                        FROM partner_api_credential
                        WHERE api_key_sha256 = ?
                          AND active = TRUE
                        """,
                        (resultSet, rowNum) -> new PartnerCredential(
                                resultSet.getString("partner_id"),
                                resultSet.getString("api_key_sha256"),
                                resultSet.getBoolean("rate_limit_exempt")),
                        apiKeySha256)
                .stream()
                .findFirst();
    }

    @Override
    public boolean existsAnyActive() {
        Boolean exists = jdbcTemplate.queryForObject(
                """
                SELECT EXISTS (
                    SELECT 1
                    FROM partner_api_credential
                    WHERE active = TRUE
                )
                """,
                Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    @Override
    public int rotate(String partnerId, String apiKeySha256) {
        Boolean rateLimitExempt = jdbcTemplate.query(
                        """
                        SELECT rate_limit_exempt
                        FROM partner_api_credential
                        WHERE partner_id = ?
                        ORDER BY active DESC, updated_at DESC
                        LIMIT 1
                        """,
                        (resultSet, rowNum) -> resultSet.getBoolean("rate_limit_exempt"),
                        partnerId)
                .stream()
                .findFirst()
                .orElse(false);
        int deactivatedCount = jdbcTemplate.update(
                """
                UPDATE partner_api_credential
                SET active = FALSE,
                    updated_at = CURRENT_TIMESTAMP
                WHERE partner_id = ?
                  AND active = TRUE
                """,
                partnerId);
        jdbcTemplate.update(
                """
                INSERT INTO partner_api_credential (
                    api_key_sha256,
                    partner_id,
                    active,
                    rate_limit_exempt
                )
                VALUES (?, ?, TRUE, ?)
                """,
                apiKeySha256,
                partnerId,
                rateLimitExempt);
        return deactivatedCount;
    }

    @Override
    public int deactivate(String partnerId) {
        return jdbcTemplate.update(
                """
                UPDATE partner_api_credential
                SET active = FALSE,
                    updated_at = CURRENT_TIMESTAMP
                WHERE partner_id = ?
                  AND active = TRUE
                """,
                partnerId);
    }
}

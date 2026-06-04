package com.hana.omnilens.security;

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
                        SELECT partner_id, api_key_sha256
                        FROM partner_api_credential
                        WHERE api_key_sha256 = ?
                          AND active = TRUE
                        """,
                        (resultSet, rowNum) -> new PartnerCredential(
                                resultSet.getString("partner_id"),
                                resultSet.getString("api_key_sha256")),
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
}

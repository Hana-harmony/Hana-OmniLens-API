package com.hana.omnilens.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {
        "omnilens.alert.dedupe.mode=in-memory",
        "omnilens.market.exchange-rate-cache.mode=in-memory",
        "omnilens.market.foreign-ownership-cache.mode=in-memory"
})
class JdbcPartnerCredentialRepositoryTest {

    @Autowired
    private PartnerCredentialRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void deleteCredentials() {
        jdbcTemplate.update("DELETE FROM partner_api_credential");
    }

    @Test
    void findsActiveCredentialByApiKeyHash() {
        jdbcTemplate.update(
                """
                INSERT INTO partner_api_credential (api_key_sha256, partner_id, active)
                VALUES (?, ?, TRUE)
                """,
                "hash-a",
                "partner-a");

        assertThat(repository.findActiveByApiKeySha256("hash-a"))
                .isPresent()
                .get()
                .extracting("partnerId", "apiKeySha256")
                .containsExactly("partner-a", "hash-a");
    }

    @Test
    void ignoresInactiveCredential() {
        jdbcTemplate.update(
                """
                INSERT INTO partner_api_credential (api_key_sha256, partner_id, active)
                VALUES (?, ?, FALSE)
                """,
                "hash-a",
                "partner-a");

        assertThat(repository.findActiveByApiKeySha256("hash-a")).isEmpty();
        assertThat(repository.existsAnyActive()).isFalse();
    }

    @Test
    void detectsAnyActiveCredential() {
        jdbcTemplate.update(
                """
                INSERT INTO partner_api_credential (api_key_sha256, partner_id, active)
                VALUES (?, ?, TRUE)
                """,
                "hash-a",
                "partner-a");

        assertThat(repository.existsAnyActive()).isTrue();
    }
}

package com.hana.omniconnect.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {
        "omni-connect.alert.dedupe.mode=in-memory",
        "omni-connect.market.exchange-rate-cache.mode=in-memory",
        "omni-connect.market.foreign-ownership-cache.mode=in-memory"
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
                .extracting("partnerId", "apiKeySha256", "rateLimitExempt")
                .containsExactly("partner-a", "hash-a", false);
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

    @Test
    void rotateDeactivatesExistingPartnerKeysAndStoresNewActiveHash() {
        jdbcTemplate.update(
                """
                INSERT INTO partner_api_credential (
                    api_key_sha256,
                    partner_id,
                    active,
                    rate_limit_exempt
                )
                VALUES (?, ?, TRUE, TRUE)
                """,
                "old-hash",
                "partner-a");

        int deactivatedCount = repository.rotate("partner-a", "new-hash");

        assertThat(deactivatedCount).isEqualTo(1);
        assertThat(repository.findActiveByApiKeySha256("old-hash")).isEmpty();
        assertThat(repository.findActiveByApiKeySha256("new-hash"))
                .isPresent()
                .get()
                .extracting("partnerId", "apiKeySha256", "rateLimitExempt")
                .containsExactly("partner-a", "new-hash", true);
    }
}

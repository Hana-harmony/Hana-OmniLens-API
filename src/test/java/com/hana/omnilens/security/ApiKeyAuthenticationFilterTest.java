package com.hana.omnilens.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "omnilens.security.api-key-enabled=true",
        "omnilens.security.api-key-sha256=",
        "omnilens.alert.dedupe.mode=in-memory",
        "management.health.redis.enabled=false"
})
@AutoConfigureMockMvc
class ApiKeyAuthenticationFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void deleteCredentials() {
        jdbcTemplate.update("DELETE FROM partner_api_credential");
    }

    @Test
    void apiFailsClosedWhenHashIsMissing() throws Exception {
        mockMvc.perform(get("/api/v1/market/stocks/005930/quote")
                        .header("X-HANA-OMNILENS-API-KEY", "test-api-key"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void apiAcceptsActivePartnerCredentialWhenGlobalHashIsMissing() throws Exception {
        jdbcTemplate.update(
                """
                INSERT INTO partner_api_credential (api_key_sha256, partner_id, active)
                VALUES (?, ?, TRUE)
                """,
                sha256Hex("partner-a-api-key"),
                "partner-a");

        mockMvc.perform(get("/openapi.yaml")
                        .header("X-HANA-OMNILENS-API-KEY", "partner-a-api-key"))
                .andExpect(status().isOk());
    }

    @Test
    void apiRejectsInactivePartnerCredential() throws Exception {
        jdbcTemplate.update(
                """
                INSERT INTO partner_api_credential (api_key_sha256, partner_id, active)
                VALUES (?, ?, FALSE)
                """,
                sha256Hex("partner-a-api-key"),
                "partner-a");

        mockMvc.perform(get("/openapi.yaml")
                        .header("X-HANA-OMNILENS-API-KEY", "partner-a-api-key"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void healthEndpointDoesNotRequireApiKey() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    private String sha256Hex(String rawValue) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(rawValue.getBytes(StandardCharsets.UTF_8)));
    }
}

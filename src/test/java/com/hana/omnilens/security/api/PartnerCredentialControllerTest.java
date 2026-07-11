package com.hana.omnilens.security.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
        "omnilens.security.api-key-sha256=4c806362b613f7496abf284146efd31da90e4b16169fe001841ca17290f427c4",
        "omnilens.alert.dedupe.mode=in-memory",
        "management.health.redis.enabled=false"
})
@AutoConfigureMockMvc
class PartnerCredentialControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void deleteCredentials() {
        jdbcTemplate.update("DELETE FROM partner_api_credential");
    }

    @Test
    void bootstrapKeyRotatesPartnerCredentialAndReturnsRawKeyOnce() throws Exception {
        insertPartnerCredential("partner-a", "old-partner-key");

        MvcResult result = mockMvc.perform(post("/api/v1/security/partners/partner-a/credentials/rotate")
                        .header("X-HANA-OMNILENS-API-KEY", "test-api-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", equalTo(true)))
                .andExpect(jsonPath("$.data.partnerId", equalTo("partner-a")))
                .andExpect(jsonPath("$.data.apiKey", matchesPattern("^[A-Za-z0-9_-]{43}$")))
                .andExpect(jsonPath("$.data.apiKeySha256Prefix", matchesPattern("^[a-f0-9]{12}$")))
                .andExpect(jsonPath("$.data.deactivatedCredentialCount", equalTo(1)))
                .andReturn();

        JsonNode payload = objectMapper.readTree(result.getResponse().getContentAsString());
        String newApiKey = payload.path("data").path("apiKey").asText();
        String newHash = sha256Hex(newApiKey);

        Integer activeOldCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM partner_api_credential WHERE api_key_sha256 = ? AND active = FALSE",
                Integer.class,
                sha256Hex("old-partner-key"));
        Integer activeNewCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM partner_api_credential WHERE api_key_sha256 = ? AND partner_id = ? AND active = TRUE",
                Integer.class,
                newHash,
                "partner-a");

        assertThat(activeOldCount).isEqualTo(1);
        assertThat(activeNewCount).isEqualTo(1);
        assertThat(payload.path("data").path("apiKeySha256Prefix").asText()).isEqualTo(newHash.substring(0, 12));

        mockMvc.perform(get("/openapi.yaml")
                        .header("X-HANA-OMNILENS-API-KEY", newApiKey))
                .andExpect(status().isOk());

        mockMvc.perform(get("/openapi.yaml")
                        .header("X-HANA-OMNILENS-API-KEY", "old-partner-key"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void partnerBoundCredentialCannotRotateCredentials() throws Exception {
        insertPartnerCredential("partner-a", "partner-a-key");

        mockMvc.perform(post("/api/v1/security/partners/partner-a/credentials/rotate")
                        .header("X-HANA-OMNILENS-API-KEY", "partner-a-key"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success", equalTo(false)))
                .andExpect(jsonPath("$.code", equalTo("AUTH_005")));
    }

    @Test
    void rejectsInvalidPartnerId() throws Exception {
        mockMvc.perform(post("/api/v1/security/partners/invalid partner/credentials/rotate")
                        .header("X-HANA-OMNILENS-API-KEY", "test-api-key"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", equalTo(false)))
                .andExpect(jsonPath("$.code", equalTo("COMMON_002")));
    }

    private void insertPartnerCredential(String partnerId, String apiKey) throws Exception {
        jdbcTemplate.update(
                """
                INSERT INTO partner_api_credential (api_key_sha256, partner_id, active)
                VALUES (?, ?, TRUE)
                """,
                sha256Hex(apiKey),
                partnerId);
    }

    private String sha256Hex(String rawValue) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(rawValue.getBytes(StandardCharsets.UTF_8)));
    }
}

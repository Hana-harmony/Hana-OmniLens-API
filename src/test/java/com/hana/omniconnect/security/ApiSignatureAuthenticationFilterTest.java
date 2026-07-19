package com.hana.omniconnect.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest(properties = {
        "omni-connect.security.rate-limit.enabled=false",
        "omni-connect.security.signature.enabled=true",
        "omni-connect.security.signature.allowed-clock-skew=5m",
        "omni-connect.security.signature.nonce-store-mode=in-memory",
        "omni-connect.alert.dedupe.mode=in-memory",
        "management.health.redis.enabled=false"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class ApiSignatureAuthenticationFilterTest {

    private static final String API_KEY = "test-api-key";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUpPartnerCredential() {
        com.hana.omniconnect.support.PartnerCredentialTestData.replace(
                jdbcTemplate, "partner-signature", API_KEY);
    }

    @Test
    void acceptsValidSignedRequest() throws Exception {
        Instant timestamp = Instant.now();

        mockMvc.perform(signedGet("/api/v1/partner/readiness", timestamp, "nonce-valid"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.data.contractVersion").value("hmac-sha256-v1"));
    }

    @Test
    void rejectsMissingSignatureHeaders() throws Exception {
        mockMvc.perform(get("/api/v1/partner/readiness")
                        .header("X-HANA-OMNI-CONNECT-API-KEY", API_KEY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsReusedNonce() throws Exception {
        Instant timestamp = Instant.now();

        mockMvc.perform(signedGet("/api/v1/partner/readiness", timestamp, "nonce-replay"))
                .andExpect(status().isOk());

        mockMvc.perform(signedGet("/api/v1/partner/readiness", timestamp, "nonce-replay"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsStaleTimestamp() throws Exception {
        Instant timestamp = Instant.now().minus(Duration.ofMinutes(10));

        mockMvc.perform(signedGet("/api/v1/partner/readiness", timestamp, "nonce-stale"))
                .andExpect(status().isUnauthorized());
    }

    private MockHttpServletRequestBuilder signedGet(String uri, Instant timestamp, String nonce) {
        byte[] body = new byte[0];
        return get(uri)
                .header("X-HANA-OMNI-CONNECT-API-KEY", API_KEY)
                .header(ApiRequestSignatureVerifier.TIMESTAMP_HEADER, timestamp.toString())
                .header(ApiRequestSignatureVerifier.NONCE_HEADER, nonce)
                .header(ApiRequestSignatureVerifier.SIGNATURE_HEADER,
                        signature("GET", uri, "", timestamp.toString(), nonce, body));
    }

    private String signature(String method, String uri, String query, String timestamp, String nonce, byte[] body) {
        String canonical = method
                + "\n"
                + (query.isBlank() ? uri : uri + "?" + query)
                + "\n"
                + timestamp
                + "\n"
                + nonce
                + "\n"
                + sha256Hex(body);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(API_KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String sha256Hex(byte[] body) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(body));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}

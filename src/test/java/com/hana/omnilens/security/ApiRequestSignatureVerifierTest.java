package com.hana.omnilens.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import com.hana.omnilens.config.ApiSignatureProperties;

class ApiRequestSignatureVerifierTest {

    private static final Instant NOW = Instant.parse("2026-06-04T00:00:00Z");
    private static final String SECRET = "test-signature-secret";
    private static final String API_KEY_FINGERPRINT = "api-key-fingerprint";

    @Test
    void verifyAcceptsBodyBoundSignature() {
        ApiRequestSignatureVerifier verifier = verifier();
        byte[] body = "{\"title\":\"삼성전자\"}".getBytes(StandardCharsets.UTF_8);
        MockHttpServletRequest request = signedRequest(
                "POST",
                "/api/v1/alerts/events",
                "",
                NOW.toString(),
                "nonce-body",
                body);

        ApiRequestSignatureVerifier.SignatureVerificationResult result =
                verifier.verify(request, API_KEY_FINGERPRINT, SECRET, body);

        assertThat(result.valid()).isTrue();
    }

    @Test
    void verifyRejectsWhenBodyHashDoesNotMatchSignature() {
        ApiRequestSignatureVerifier verifier = verifier();
        byte[] signedBody = "{\"title\":\"삼성전자\"}".getBytes(StandardCharsets.UTF_8);
        byte[] tamperedBody = "{\"title\":\"하이닉스\"}".getBytes(StandardCharsets.UTF_8);
        MockHttpServletRequest request = signedRequest(
                "POST",
                "/api/v1/alerts/events",
                "",
                NOW.toString(),
                "nonce-tampered",
                signedBody);

        ApiRequestSignatureVerifier.SignatureVerificationResult result =
                verifier.verify(request, API_KEY_FINGERPRINT, SECRET, tamperedBody);

        assertThat(result.valid()).isFalse();
        assertThat(result.message()).isEqualTo("Invalid request signature");
    }

    private ApiRequestSignatureVerifier verifier() {
        ApiSignatureProperties properties =
                new ApiSignatureProperties(
                        true,
                        Duration.ofMinutes(5),
                        ApiSignatureProperties.NonceStoreMode.IN_MEMORY,
                        10_000);
        return new ApiRequestSignatureVerifier(
                properties,
                new InMemoryApiSignatureNonceStore(properties, Clock.fixed(NOW, ZoneOffset.UTC)),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private MockHttpServletRequest signedRequest(
            String method,
            String uri,
            String query,
            String timestamp,
            String nonce,
            byte[] body) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        if (!query.isBlank()) {
            request.setQueryString(query);
        }
        request.addHeader(ApiRequestSignatureVerifier.TIMESTAMP_HEADER, timestamp);
        request.addHeader(ApiRequestSignatureVerifier.NONCE_HEADER, nonce);
        request.addHeader(ApiRequestSignatureVerifier.SIGNATURE_HEADER, signature(method, uri, query, timestamp, nonce, body));
        return request;
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
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
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

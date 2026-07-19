package com.hana.omniconnect.security;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.hana.omniconnect.config.ApiSignatureProperties;

@Component
public class ApiRequestSignatureVerifier {

    public static final String TIMESTAMP_HEADER = "X-HANA-OMNI-CONNECT-TIMESTAMP";
    public static final String NONCE_HEADER = "X-HANA-OMNI-CONNECT-NONCE";
    public static final String SIGNATURE_HEADER = "X-HANA-OMNI-CONNECT-SIGNATURE";

    private final ApiSignatureProperties properties;
    private final ApiSignatureNonceStore nonceStore;
    private final Clock clock;

    @Autowired
    public ApiRequestSignatureVerifier(ApiSignatureProperties properties, ApiSignatureNonceStore nonceStore) {
        this(properties, nonceStore, Clock.systemUTC());
    }

    ApiRequestSignatureVerifier(
            ApiSignatureProperties properties,
            ApiSignatureNonceStore nonceStore,
            Clock clock) {
        this.properties = properties;
        this.nonceStore = nonceStore;
        this.clock = clock;
    }

    public SignatureVerificationResult verify(
            HttpServletRequest request,
            String apiKeyFingerprint,
            String signingSecret,
            byte[] body) {
        if (!properties.enabled()) {
            return SignatureVerificationResult.ok();
        }
        String timestampHeader = request.getHeader(TIMESTAMP_HEADER);
        String nonce = request.getHeader(NONCE_HEADER);
        String providedSignature = normalizeSignature(request.getHeader(SIGNATURE_HEADER));
        if (!StringUtils.hasText(timestampHeader)
                || !StringUtils.hasText(nonce)
                || !StringUtils.hasText(providedSignature)) {
            return SignatureVerificationResult.unauthorized("Missing request signature");
        }

        Instant timestamp;
        try {
            timestamp = Instant.parse(timestampHeader);
        } catch (DateTimeParseException exception) {
            return SignatureVerificationResult.unauthorized("Invalid request signature timestamp");
        }

        Instant now = Instant.now(clock);
        Duration skew = Duration.between(timestamp, now).abs();
        if (skew.compareTo(properties.allowedClockSkew()) > 0) {
            return SignatureVerificationResult.unauthorized("Request signature timestamp is outside allowed skew");
        }

        String expectedSignature;
        try {
            expectedSignature = sign(canonicalRequest(request, timestampHeader, nonce, body), signingSecret);
        } catch (IllegalStateException exception) {
            return SignatureVerificationResult.serviceUnavailable(exception.getMessage());
        }

        if (!constantTimeEquals(providedSignature, expectedSignature)) {
            return SignatureVerificationResult.unauthorized("Invalid request signature");
        }
        boolean storedNonce;
        try {
            storedNonce = nonceStore.remember(apiKeyFingerprint, nonce, timestamp.plus(properties.allowedClockSkew()));
        } catch (RuntimeException exception) {
            return SignatureVerificationResult.serviceUnavailable("Request signature nonce store is unavailable");
        }
        if (!storedNonce) {
            return SignatureVerificationResult.unauthorized("Request signature nonce was already used");
        }
        return SignatureVerificationResult.ok();
    }

    private String canonicalRequest(HttpServletRequest request, String timestamp, String nonce, byte[] body) {
        return request.getMethod().toUpperCase()
                + "\n"
                + requestUriWithQuery(request)
                + "\n"
                + timestamp
                + "\n"
                + nonce
                + "\n"
                + sha256Hex(body);
    }

    private String requestUriWithQuery(HttpServletRequest request) {
        String queryString = request.getQueryString();
        if (!StringUtils.hasText(queryString)) {
            return request.getRequestURI();
        }
        return request.getRequestURI() + "?" + queryString;
    }

    private String sign(String canonicalRequest, String signingSecret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(canonicalRequest.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
            throw new IllegalStateException("HmacSHA256 request signing is not available", exception);
        }
    }

    private String sha256Hex(byte[] body) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(body));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 body digest is not available", exception);
        }
    }

    private boolean constantTimeEquals(String providedSignature, String expectedSignature) {
        byte[] provided = providedSignature.getBytes(StandardCharsets.UTF_8);
        byte[] expected = expectedSignature.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(provided, expected);
    }

    private String normalizeSignature(String signature) {
        if (!StringUtils.hasText(signature)) {
            return "";
        }
        return signature.startsWith("sha256=") ? signature.substring("sha256=".length()) : signature;
    }

    public record SignatureVerificationResult(boolean valid, HttpStatus status, String message) {

        private static SignatureVerificationResult ok() {
            return new SignatureVerificationResult(true, HttpStatus.OK, "");
        }

        private static SignatureVerificationResult unauthorized(String message) {
            return new SignatureVerificationResult(false, HttpStatus.UNAUTHORIZED, message);
        }

        private static SignatureVerificationResult serviceUnavailable(String message) {
            return new SignatureVerificationResult(false, HttpStatus.SERVICE_UNAVAILABLE, message);
        }
    }
}

package com.hana.omniconnect.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.springframework.jdbc.core.JdbcTemplate;

public final class PartnerCredentialTestData {

    private PartnerCredentialTestData() {
    }

    public static void replace(JdbcTemplate jdbcTemplate, String partnerId, String apiKey) {
        String apiKeySha256 = sha256Hex(apiKey);
        jdbcTemplate.update(
                "DELETE FROM partner_api_credential WHERE api_key_sha256 = ? OR partner_id = ?",
                apiKeySha256,
                partnerId);
        jdbcTemplate.update(
                """
                INSERT INTO partner_api_credential (api_key_sha256, partner_id, active)
                VALUES (?, ?, TRUE)
                """,
                apiKeySha256,
                partnerId);
    }

    private static String sha256Hex(String rawValue) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawValue.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}

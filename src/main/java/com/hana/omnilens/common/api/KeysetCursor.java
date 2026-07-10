package com.hana.omnilens.common.api;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;

import org.springframework.util.StringUtils;

public record KeysetCursor(Instant publishedAt, Instant createdAt, String id) {

    private static final int MAX_CURSOR_LENGTH = 512;

    public static String encode(Instant publishedAt, Instant createdAt, String id) {
        String payload = publishedAt + "\n" + createdAt + "\n" + id;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    public static KeysetCursor decode(String cursor) {
        if (!StringUtils.hasText(cursor) || cursor.length() > MAX_CURSOR_LENGTH) {
            throw new IllegalArgumentException("invalid cursor");
        }
        try {
            String payload = new String(
                    Base64.getUrlDecoder().decode(cursor),
                    StandardCharsets.UTF_8);
            String[] parts = payload.split("\\n", -1);
            if (parts.length != 3 || !StringUtils.hasText(parts[2]) || parts[2].length() > 80) {
                throw new IllegalArgumentException("invalid cursor");
            }
            return new KeysetCursor(
                    Instant.parse(parts[0]),
                    Instant.parse(parts[1]),
                    parts[2]);
        } catch (IllegalArgumentException | DateTimeParseException exception) {
            throw new IllegalArgumentException("invalid cursor", exception);
        }
    }
}

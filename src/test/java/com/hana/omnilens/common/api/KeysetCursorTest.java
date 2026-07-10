package com.hana.omnilens.common.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class KeysetCursorTest {

    @Test
    void roundTripsOpaqueCursor() {
        Instant publishedAt = Instant.parse("2026-07-10T06:00:00Z");
        Instant createdAt = Instant.parse("2026-07-10T06:01:00Z");

        String encoded = KeysetCursor.encode(publishedAt, createdAt, "event-1");

        assertThat(KeysetCursor.decode(encoded))
                .isEqualTo(new KeysetCursor(publishedAt, createdAt, "event-1"));
    }

    @Test
    void rejectsMalformedCursor() {
        assertThatThrownBy(() -> KeysetCursor.decode("not-a-cursor"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

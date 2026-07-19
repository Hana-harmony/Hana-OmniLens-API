package com.hana.omniconnect.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "omni-connect.alert.dedupe")
public record AlertDedupeProperties(
        Mode mode,
        Duration ttl,
        int maxInMemoryEntries
) {

    public AlertDedupeProperties {
        mode = mode == null ? Mode.REDIS : mode;
        ttl = ttl == null ? Duration.ofHours(24) : ttl;
        maxInMemoryEntries = maxInMemoryEntries <= 0 ? 10_000 : maxInMemoryEntries;
    }

    public enum Mode {
        REDIS,
        IN_MEMORY
    }
}

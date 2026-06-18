package com.hana.omnilens.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "omnilens.market.foreign-ownership-cache")
public record ForeignOwnershipCacheProperties(
        Mode mode,
        Duration ttl
) {

    public ForeignOwnershipCacheProperties {
        mode = mode == null ? Mode.REDIS : mode;
        ttl = ttl == null ? Duration.ofHours(24) : ttl;
    }

    public enum Mode {
        REDIS,
        IN_MEMORY
    }
}

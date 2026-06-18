package com.hana.omnilens.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "omnilens.market.exchange-rate-cache")
public record ExchangeRateCacheProperties(
        Mode mode,
        Duration ttl
) {

    public ExchangeRateCacheProperties {
        mode = mode == null ? Mode.REDIS : mode;
        ttl = ttl == null ? Duration.ofHours(24) : ttl;
    }

    public enum Mode {
        REDIS,
        IN_MEMORY
    }
}

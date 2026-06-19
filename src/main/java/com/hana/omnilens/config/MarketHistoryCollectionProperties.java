package com.hana.omnilens.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "omnilens.market.history-collection")
public record MarketHistoryCollectionProperties(
        boolean enabled,
        long fixedDelayMs,
        int baseDateOffsetDays
) {

    public MarketHistoryCollectionProperties {
        fixedDelayMs = fixedDelayMs <= 0 ? 86_400_000L : fixedDelayMs;
        baseDateOffsetDays = Math.max(baseDateOffsetDays, 1);
    }
}

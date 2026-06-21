package com.hana.omnilens.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "omnilens.market.history-collection")
public record MarketHistoryCollectionProperties(
        boolean enabled,
        long fixedDelayMs,
        int baseDateOffsetDays,
        Provider provider
) {

    public MarketHistoryCollectionProperties {
        fixedDelayMs = fixedDelayMs <= 0 ? 86_400_000L : fixedDelayMs;
        baseDateOffsetDays = Math.max(baseDateOffsetDays, 1);
        provider = provider == null ? Provider.KRX_OPEN_API_WITH_KIS_BACKUP : provider;
    }

    public enum Provider {
        KRX_OPEN_API_WITH_KIS_BACKUP,
        KRX_OPEN_API,
        KIS_DAILY_CHART
    }
}

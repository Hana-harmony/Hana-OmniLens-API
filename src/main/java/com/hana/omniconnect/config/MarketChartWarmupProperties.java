package com.hana.omniconnect.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "omni-connect.market.chart-warmup")
public record MarketChartWarmupProperties(
        Boolean enabled,
        long fixedDelayMs,
        long initialDelayMs,
        int baseDateOffsetDays,
        int dailyLookbackDays,
        int intradayLookbackDays,
        int stockLimit,
        List<String> stockCodes,
        long requestDelayMs,
        Boolean dailyEnabled,
        Boolean intradayEnabled
) {

    public MarketChartWarmupProperties {
        fixedDelayMs = fixedDelayMs <= 0 ? 300_000L : fixedDelayMs;
        initialDelayMs = initialDelayMs < 0 ? 10_000L : initialDelayMs;
        baseDateOffsetDays = Math.max(baseDateOffsetDays, 0);
        dailyLookbackDays = dailyLookbackDays <= 0 ? 30 : dailyLookbackDays;
        intradayLookbackDays = intradayLookbackDays <= 0 ? 7 : intradayLookbackDays;
        stockLimit = stockLimit <= 0 ? 30 : stockLimit;
        stockCodes = stockCodes == null ? List.of() : List.copyOf(stockCodes);
        requestDelayMs = Math.max(requestDelayMs, 0L);
    }

    public boolean isEnabled() {
        return !Boolean.FALSE.equals(enabled);
    }

    public boolean isDailyEnabled() {
        return !Boolean.FALSE.equals(dailyEnabled);
    }

    public boolean isIntradayEnabled() {
        return !Boolean.FALSE.equals(intradayEnabled);
    }
}

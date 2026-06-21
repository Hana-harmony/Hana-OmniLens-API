package com.hana.omnilens.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "omnilens.market.foreign-ownership-refresh")
public record ForeignOwnershipRefreshProperties(
        boolean enabled,
        long fixedDelayMs,
        long initialDelayMs,
        long requestDelayMs,
        int baseDateOffsetDays,
        int stockLimit,
        List<String> stockCodes
) {

    private static final int DEFAULT_STOCK_LIMIT = 2_500;
    private static final long DEFAULT_INITIAL_DELAY_MS = 60_000L;
    private static final long DEFAULT_REQUEST_DELAY_MS = 1_200L;

    public ForeignOwnershipRefreshProperties {
        fixedDelayMs = fixedDelayMs <= 0 ? 86_400_000L : fixedDelayMs;
        initialDelayMs = initialDelayMs < 0 ? DEFAULT_INITIAL_DELAY_MS : initialDelayMs;
        requestDelayMs = requestDelayMs <= 0 ? DEFAULT_REQUEST_DELAY_MS : Math.min(requestDelayMs, 60_000L);
        baseDateOffsetDays = Math.max(baseDateOffsetDays, 1);
        stockLimit = stockLimit <= 0 ? DEFAULT_STOCK_LIMIT : Math.min(stockLimit, DEFAULT_STOCK_LIMIT);
        stockCodes = stockCodes == null
                ? List.of()
                : stockCodes.stream()
                        .filter(StringUtils::hasText)
                        .map(String::trim)
                        .filter(stockCode -> stockCode.matches("\\d{6}"))
                        .distinct()
                        .toList();
    }
}

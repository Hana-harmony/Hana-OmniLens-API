package com.hana.omniconnect.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "omni-connect.market.foreign-ownership-refresh")
public record ForeignOwnershipRefreshProperties(
        boolean enabled,
        long fixedDelayMs,
        long initialDelayMs,
        String cron,
        long requestDelayMs,
        int baseDateOffsetDays,
        int backfillLookbackDays,
        int stockLimit,
        List<String> stockCodes
) {

    private static final int DEFAULT_STOCK_LIMIT = 5_000;
    private static final long DEFAULT_INITIAL_DELAY_MS = 60_000L;
    private static final long DEFAULT_REQUEST_DELAY_MS = 1_200L;
    private static final int DEFAULT_BACKFILL_LOOKBACK_DAYS = 400;
    private static final String DEFAULT_CRON = "0 10 8,16 * * MON-FRI";

    public ForeignOwnershipRefreshProperties {
        fixedDelayMs = fixedDelayMs <= 0 ? 86_400_000L : fixedDelayMs;
        initialDelayMs = initialDelayMs < 0 ? DEFAULT_INITIAL_DELAY_MS : initialDelayMs;
        cron = StringUtils.hasText(cron) ? cron.trim() : DEFAULT_CRON;
        requestDelayMs = requestDelayMs <= 0 ? DEFAULT_REQUEST_DELAY_MS : Math.min(requestDelayMs, 60_000L);
        baseDateOffsetDays = Math.max(baseDateOffsetDays, 1);
        backfillLookbackDays = backfillLookbackDays <= 0
                ? DEFAULT_BACKFILL_LOOKBACK_DAYS
                : Math.min(backfillLookbackDays, 2_000);
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

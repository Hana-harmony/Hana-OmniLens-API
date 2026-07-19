package com.hana.omniconnect.config;

import java.util.List;
import java.util.Locale;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "omni-connect.market.exchange-rate-refresh")
public record ExchangeRateRefreshProperties(
        boolean enabled,
        long fixedDelayMs,
        int baseDateOffsetDays,
        List<String> currencies
) {

    public ExchangeRateRefreshProperties {
        fixedDelayMs = fixedDelayMs <= 0 ? 300_000L : fixedDelayMs;
        baseDateOffsetDays = Math.max(baseDateOffsetDays, 0);
        currencies = currencies == null
                ? List.of()
                : currencies.stream()
                        .filter(StringUtils::hasText)
                        .map(String::trim)
                        .map(currency -> currency.toUpperCase(Locale.ROOT))
                        .distinct()
                        .toList();
    }
}

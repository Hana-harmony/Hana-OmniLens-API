package com.hana.omnilens.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "omnilens.market-news.scheduler")
public record MarketNewsCollectionProperties(
        boolean enabled,
        long fixedDelayMs,
        long initialDelayMs,
        int display,
        List<String> queries
) {

    public MarketNewsCollectionProperties {
        fixedDelayMs = fixedDelayMs <= 0 ? 300_000L : fixedDelayMs;
        initialDelayMs = initialDelayMs < 0 ? 60_000L : initialDelayMs;
        display = display <= 0 ? 10 : display;
        queries = queries == null || queries.isEmpty()
                ? List.of("한국 증시", "코스피 코스닥", "국내 증시")
                : queries.stream()
                        .filter(query -> query != null && !query.isBlank())
                        .distinct()
                        .toList();
    }
}

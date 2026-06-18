package com.hana.omnilens.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "omnilens.security.rate-limit")
public record ApiRateLimitProperties(
        boolean enabled,
        int capacity,
        int refillTokens,
        Duration refillPeriod,
        int maxBuckets
) {

    public ApiRateLimitProperties {
        capacity = capacity <= 0 ? 120 : capacity;
        refillTokens = refillTokens <= 0 ? capacity : refillTokens;
        refillPeriod = refillPeriod == null ? Duration.ofMinutes(1) : refillPeriod;
        maxBuckets = maxBuckets <= 0 ? 10_000 : maxBuckets;
    }
}

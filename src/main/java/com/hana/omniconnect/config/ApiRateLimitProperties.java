package com.hana.omniconnect.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "omni-connect.security.rate-limit")
public record ApiRateLimitProperties(
        boolean enabled,
        int maxRequests,
        Duration window
) {

    public ApiRateLimitProperties {
        maxRequests = maxRequests <= 0 ? 120 : maxRequests;
        window = window == null || window.isNegative() || window.isZero()
                ? Duration.ofMinutes(1)
                : window;
    }
}

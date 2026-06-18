package com.hana.omnilens.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "omnilens.market.kis-realtime")
public record KisRealtimeProperties(
        boolean enabled,
        List<String> stockCodes
) {

    public KisRealtimeProperties {
        stockCodes = stockCodes == null
                ? List.of()
                : stockCodes.stream()
                        .filter(StringUtils::hasText)
                        .toList();
    }
}

package com.hana.omnilens.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "omnilens.market.kis-realtime")
public record KisRealtimeProperties(
        boolean enabled,
        List<String> stockCodes,
        List<String> indexCodes,
        int stockLimit,
        int shardSize,
        boolean orderBookEnabled,
        boolean afterHoursEnabled
) {

    public KisRealtimeProperties(boolean enabled, List<String> stockCodes) {
        this(enabled, stockCodes, List.of(), 5000, 40, false, false);
    }

    public KisRealtimeProperties(
            boolean enabled,
            List<String> stockCodes,
            int stockLimit,
            int shardSize,
            boolean orderBookEnabled) {
        this(enabled, stockCodes, List.of(), stockLimit, shardSize, orderBookEnabled, false);
    }

    public KisRealtimeProperties(
            boolean enabled,
            List<String> stockCodes,
            int stockLimit,
            int shardSize,
            boolean orderBookEnabled,
            boolean afterHoursEnabled) {
        this(enabled, stockCodes, List.of(), stockLimit, shardSize, orderBookEnabled, afterHoursEnabled);
    }

    @ConstructorBinding
    public KisRealtimeProperties {
        stockCodes = stockCodes == null
                ? List.of()
                : stockCodes.stream()
                        .filter(StringUtils::hasText)
                        .toList();
        indexCodes = indexCodes == null
                ? List.of()
                : indexCodes.stream()
                        .filter(StringUtils::hasText)
                        .toList();
        stockLimit = stockLimit < 0 ? 5000 : stockLimit;
        shardSize = shardSize <= 0 ? 40 : shardSize;
    }
}

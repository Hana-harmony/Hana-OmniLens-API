package com.hana.omniconnect.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "omni-connect.market.foreign-ownership-prediction-precompute")
public record ForeignOwnershipPredictionPrecomputeProperties(
        Boolean enabled,
        Boolean triggerAfterRefresh,
        int stockLimit
) {

    public ForeignOwnershipPredictionPrecomputeProperties {
        enabled = enabled == null ? Boolean.TRUE : enabled;
        triggerAfterRefresh = triggerAfterRefresh == null ? Boolean.TRUE : triggerAfterRefresh;
        stockLimit = stockLimit <= 0 ? 5_000 : Math.min(stockLimit, 5_000);
    }
}

package com.hana.omniconnect.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "omni-connect.market.foreign-ownership-model-training")
public record ForeignOwnershipModelTrainingProperties(
        Boolean enabled,
        Boolean triggerAfterRefresh,
        Duration connectTimeout,
        Duration readTimeout,
        String maintenanceToken,
        int minimumPromotableStockCount,
        int minimumPromotableHistoryDays,
        int minimumPromotableObservations,
        int maxModelTrainingSamples
) {

    public ForeignOwnershipModelTrainingProperties {
        enabled = enabled == null ? Boolean.TRUE : enabled;
        triggerAfterRefresh = triggerAfterRefresh == null ? Boolean.TRUE : triggerAfterRefresh;
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(2) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofMinutes(20) : readTimeout;
        maintenanceToken = StringUtils.hasText(maintenanceToken) ? maintenanceToken.trim() : "";
        minimumPromotableStockCount = minimumPromotableStockCount <= 0
                ? 29
                : minimumPromotableStockCount;
        minimumPromotableHistoryDays = minimumPromotableHistoryDays <= 0
                ? 2_500
                : minimumPromotableHistoryDays;
        minimumPromotableObservations = minimumPromotableObservations <= 0
                ? 50_000
                : minimumPromotableObservations;
        maxModelTrainingSamples = maxModelTrainingSamples <= 0
                ? 250_000
                : maxModelTrainingSamples;
    }
}

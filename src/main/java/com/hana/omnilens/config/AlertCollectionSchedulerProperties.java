package com.hana.omnilens.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "omnilens.alert.scheduler")
public record AlertCollectionSchedulerProperties(
        boolean enabled,
        long fixedDelayMs,
        int newsDisplay,
        int disclosureLookbackDays,
        List<PartnerWatchlist> watchlists,
        boolean defaultUniverseEnabled,
        String defaultUniversePartnerId,
        int priorityStockLimit,
        boolean includeForeignOwnershipRestrictedStocks,
        int collectionBatchSize
) {

    private static final String DEFAULT_UNIVERSE_PARTNER_ID = "omnilens-default-universe";
    private static final int DEFAULT_PRIORITY_STOCK_LIMIT = 30;
    private static final int DEFAULT_COLLECTION_BATCH_SIZE = 20;
    private static final int MAX_COLLECTION_BATCH_SIZE = 20;

    public AlertCollectionSchedulerProperties {
        fixedDelayMs = fixedDelayMs <= 0 ? 300_000L : fixedDelayMs;
        newsDisplay = newsDisplay <= 0 ? 10 : newsDisplay;
        disclosureLookbackDays = disclosureLookbackDays <= 0 ? 7 : disclosureLookbackDays;
        watchlists = watchlists == null ? List.of() : List.copyOf(watchlists);
        defaultUniversePartnerId = StringUtils.hasText(defaultUniversePartnerId)
                ? defaultUniversePartnerId
                : DEFAULT_UNIVERSE_PARTNER_ID;
        priorityStockLimit = priorityStockLimit <= 0 ? DEFAULT_PRIORITY_STOCK_LIMIT : priorityStockLimit;
        collectionBatchSize = collectionBatchSize <= 0 ? DEFAULT_COLLECTION_BATCH_SIZE : collectionBatchSize;
        collectionBatchSize = Math.min(collectionBatchSize, MAX_COLLECTION_BATCH_SIZE);
    }

    public record PartnerWatchlist(
            String partnerId,
            List<String> stockCodes
    ) {

        public PartnerWatchlist {
            stockCodes = stockCodes == null ? List.of() : List.copyOf(stockCodes);
        }
    }
}

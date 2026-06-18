package com.hana.omnilens.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "omnilens.alert.scheduler")
public record AlertCollectionSchedulerProperties(
        boolean enabled,
        long fixedDelayMs,
        int newsDisplay,
        int disclosureLookbackDays,
        List<PartnerWatchlist> watchlists
) {

    public AlertCollectionSchedulerProperties {
        fixedDelayMs = fixedDelayMs <= 0 ? 300_000L : fixedDelayMs;
        newsDisplay = newsDisplay <= 0 ? 10 : newsDisplay;
        disclosureLookbackDays = disclosureLookbackDays <= 0 ? 7 : disclosureLookbackDays;
        watchlists = watchlists == null ? List.of() : List.copyOf(watchlists);
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

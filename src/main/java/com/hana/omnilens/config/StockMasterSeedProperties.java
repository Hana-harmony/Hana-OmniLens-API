package com.hana.omnilens.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "omnilens.market.stock-master.seed")
public record StockMasterSeedProperties(
        boolean enabled,
        String location
) {

    public StockMasterSeedProperties {
        location = location == null || location.isBlank()
                ? "classpath:data/stock-master-seed.csv"
                : location;
    }
}

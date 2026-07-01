package com.hana.omnilens.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        StockMasterSeedProperties.class,
        MarketHistoryCollectionProperties.class,
        MarketNewsCollectionProperties.class
})
public class MarketDataConfiguration {
}

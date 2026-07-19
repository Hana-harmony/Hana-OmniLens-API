package com.hana.omniconnect.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        StockMasterSeedProperties.class,
        MarketHistoryCollectionProperties.class,
        MarketChartWarmupProperties.class,
        MarketNewsCollectionProperties.class
})
public class MarketDataConfiguration {
}

package com.hana.omnilens.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        ExternalProviderProperties.class,
        HannahAiProperties.class,
        AlertDedupeProperties.class,
        AlertCollectionSchedulerProperties.class,
        KisRealtimeProperties.class,
        ExchangeRateRefreshProperties.class,
        ExchangeRateCacheProperties.class,
        ForeignOwnershipCacheProperties.class
})
public class ExternalProviderConfiguration {
}

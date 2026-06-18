package com.hana.omnilens.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

@Configuration
@EnableConfigurationProperties({
        ExternalProviderProperties.class,
        HannahAiProperties.class,
        AlertDedupeProperties.class,
        AlertCollectionSchedulerProperties.class,
        KisRealtimeProperties.class,
        ExchangeRateRefreshProperties.class,
        ExchangeRateCacheProperties.class,
        ForeignOwnershipCacheProperties.class,
        ExternalProviderResilienceProperties.class,
        FrankfurterProperties.class,
        KrxOpenApiProperties.class
})
public class ExternalProviderConfiguration {

    @Bean
    RestClientCustomizer externalProviderTimeoutCustomizer(ExternalProviderResilienceProperties properties) {
        return builder -> {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(properties.connectTimeout());
            factory.setReadTimeout(properties.readTimeout());
            builder.requestFactory(factory);
        };
    }
}

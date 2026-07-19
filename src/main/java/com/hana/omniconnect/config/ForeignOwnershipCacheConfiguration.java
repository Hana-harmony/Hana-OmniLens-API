package com.hana.omniconnect.config;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.hana.omniconnect.market.application.ForeignOwnershipSnapshotCache;
import com.hana.omniconnect.market.application.ForeignOwnershipPredictionCache;
import com.hana.omniconnect.market.application.InMemoryForeignOwnershipSnapshotCache;
import com.hana.omniconnect.market.application.InMemoryForeignOwnershipPredictionCache;
import com.hana.omniconnect.market.application.RedisForeignOwnershipSnapshotCache;
import com.hana.omniconnect.market.application.RedisForeignOwnershipPredictionCache;

@Configuration
public class ForeignOwnershipCacheConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ForeignOwnershipSnapshotCache foreignOwnershipSnapshotCache(
            ForeignOwnershipCacheProperties properties,
            ObjectProvider<StringRedisTemplate> redisTemplateProvider,
            ObjectMapper objectMapper) {
        ForeignOwnershipSnapshotCache fallbackCache = new InMemoryForeignOwnershipSnapshotCache();
        if (properties.mode() == ForeignOwnershipCacheProperties.Mode.IN_MEMORY) {
            return fallbackCache;
        }

        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate == null) {
            return fallbackCache;
        }

        return new RedisForeignOwnershipSnapshotCache(
                redisTemplate,
                objectMapper,
                fallbackCache,
                properties.ttl());
    }

    @Bean
    @ConditionalOnMissingBean
    public ForeignOwnershipPredictionCache foreignOwnershipPredictionCache(
            ForeignOwnershipCacheProperties properties,
            ObjectProvider<StringRedisTemplate> redisTemplateProvider,
            ObjectMapper objectMapper) {
        ForeignOwnershipPredictionCache fallbackCache = new InMemoryForeignOwnershipPredictionCache();
        if (properties.mode() == ForeignOwnershipCacheProperties.Mode.IN_MEMORY) {
            return fallbackCache;
        }

        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate == null) {
            return fallbackCache;
        }

        return new RedisForeignOwnershipPredictionCache(
                redisTemplate,
                objectMapper,
                fallbackCache,
                properties.ttl());
    }
}

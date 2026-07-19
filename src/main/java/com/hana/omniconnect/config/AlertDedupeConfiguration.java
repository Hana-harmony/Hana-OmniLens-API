package com.hana.omniconnect.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.hana.omniconnect.alert.application.AlertDedupeStore;
import com.hana.omniconnect.alert.application.InMemoryAlertDedupeStore;
import com.hana.omniconnect.alert.application.RedisAlertDedupeStore;

@Configuration
public class AlertDedupeConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AlertDedupeStore alertDedupeStore(
            AlertDedupeProperties properties,
            ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        AlertDedupeStore fallbackStore = new InMemoryAlertDedupeStore(properties.maxInMemoryEntries());
        if (properties.mode() == AlertDedupeProperties.Mode.IN_MEMORY) {
            return fallbackStore;
        }
        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate == null) {
            return fallbackStore;
        }
        return new RedisAlertDedupeStore(redisTemplate, fallbackStore, properties.ttl());
    }
}

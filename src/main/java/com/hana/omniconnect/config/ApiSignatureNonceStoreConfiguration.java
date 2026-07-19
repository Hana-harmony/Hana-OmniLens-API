package com.hana.omniconnect.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.hana.omniconnect.security.ApiSignatureNonceStore;
import com.hana.omniconnect.security.InMemoryApiSignatureNonceStore;
import com.hana.omniconnect.security.RedisApiSignatureNonceStore;
import com.hana.omniconnect.security.UnavailableApiSignatureNonceStore;

@Configuration
public class ApiSignatureNonceStoreConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ApiSignatureNonceStore apiSignatureNonceStore(
            ApiSignatureProperties properties,
            ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        if (properties.nonceStoreMode() == ApiSignatureProperties.NonceStoreMode.IN_MEMORY) {
            return new InMemoryApiSignatureNonceStore(properties);
        }

        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate == null) {
            return new UnavailableApiSignatureNonceStore();
        }
        return new RedisApiSignatureNonceStore(redisTemplate);
    }
}

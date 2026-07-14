package com.hana.omnilens.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.hana.omnilens.security.ApiSignatureNonceStore;
import com.hana.omnilens.security.InMemoryApiSignatureNonceStore;
import com.hana.omnilens.security.RedisApiSignatureNonceStore;
import com.hana.omnilens.security.UnavailableApiSignatureNonceStore;

class ApiSignatureNonceStoreConfigurationTest {

    private final ApiSignatureNonceStoreConfiguration configuration =
            new ApiSignatureNonceStoreConfiguration();

    @Test
    void redisModeUsesRedisStoreWhenTemplateExists() {
        ApiSignatureNonceStore store = configuration.apiSignatureNonceStore(
                properties(ApiSignatureProperties.NonceStoreMode.REDIS),
                provider(mock(StringRedisTemplate.class)));

        assertThat(store).isInstanceOf(RedisApiSignatureNonceStore.class);
    }

    @Test
    void redisModeFailsClosedWhenTemplateIsMissing() {
        ApiSignatureNonceStore store = configuration.apiSignatureNonceStore(
                properties(ApiSignatureProperties.NonceStoreMode.REDIS),
                provider(null));

        assertThat(store).isInstanceOf(UnavailableApiSignatureNonceStore.class);
    }

    @Test
    void inMemoryModeUsesLocalStore() {
        ApiSignatureNonceStore store = configuration.apiSignatureNonceStore(
                properties(ApiSignatureProperties.NonceStoreMode.IN_MEMORY),
                provider(mock(StringRedisTemplate.class)));

        assertThat(store).isInstanceOf(InMemoryApiSignatureNonceStore.class);
    }

    private ApiSignatureProperties properties(ApiSignatureProperties.NonceStoreMode mode) {
        return new ApiSignatureProperties(
                true,
                Duration.ofMinutes(5),
                mode,
                10_000);
    }

    private ObjectProvider<StringRedisTemplate> provider(StringRedisTemplate redisTemplate) {
        return new ObjectProvider<>() {
            @Override
            public StringRedisTemplate getObject(Object... args) {
                return redisTemplate;
            }

            @Override
            public StringRedisTemplate getIfAvailable() {
                return redisTemplate;
            }

            @Override
            public StringRedisTemplate getIfUnique() {
                return redisTemplate;
            }

            @Override
            public StringRedisTemplate getObject() {
                return redisTemplate;
            }
        };
    }
}

package com.hana.omniconnect.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class ExchangeRateCachePropertiesTest {

    @Test
    void defaultsToRedisModeWithDailyTtl() {
        ExchangeRateCacheProperties properties = new ExchangeRateCacheProperties(null, null);

        assertThat(properties.mode()).isEqualTo(ExchangeRateCacheProperties.Mode.REDIS);
        assertThat(properties.ttl()).isEqualTo(Duration.ofHours(24));
    }
}

package com.hana.omniconnect.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class ForeignOwnershipCachePropertiesTest {

    @Test
    void defaultsToRedisModeWithDailyTtl() {
        ForeignOwnershipCacheProperties properties = new ForeignOwnershipCacheProperties(null, null);

        assertThat(properties.mode()).isEqualTo(ForeignOwnershipCacheProperties.Mode.REDIS);
        assertThat(properties.ttl()).isEqualTo(Duration.ofHours(24));
    }
}

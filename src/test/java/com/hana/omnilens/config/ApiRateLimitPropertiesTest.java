package com.hana.omnilens.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class ApiRateLimitPropertiesTest {

    @Test
    void defaultsAreBoundedAndEnabled() {
        ApiRateLimitProperties properties = new ApiRateLimitProperties(true, 0, 0, null, 0);

        assertThat(properties.enabled()).isTrue();
        assertThat(properties.capacity()).isEqualTo(120);
        assertThat(properties.refillTokens()).isEqualTo(120);
        assertThat(properties.refillPeriod()).isEqualTo(Duration.ofMinutes(1));
        assertThat(properties.maxBuckets()).isEqualTo(10_000);
    }
}

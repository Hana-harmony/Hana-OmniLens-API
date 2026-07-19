package com.hana.omniconnect.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class ApiRateLimitPropertiesTest {

    @Test
    void defaultsAreBoundedAndEnabled() {
        ApiRateLimitProperties properties = new ApiRateLimitProperties(true, 0, null);

        assertThat(properties.enabled()).isTrue();
        assertThat(properties.maxRequests()).isEqualTo(120);
        assertThat(properties.window()).isEqualTo(Duration.ofMinutes(1));
    }
}

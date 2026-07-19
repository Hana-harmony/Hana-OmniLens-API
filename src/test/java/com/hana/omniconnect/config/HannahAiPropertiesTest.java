package com.hana.omniconnect.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class HannahAiPropertiesTest {

    @Test
    void defaultPropertiesUseInternalAiServiceProfile() {
        HannahAiProperties properties = new HannahAiProperties(null, null, null);

        assertThat(properties.baseUrl().toString()).isEqualTo("http://hannah-montana-ai:8000");
        assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(2));
        assertThat(properties.readTimeout()).isEqualTo(Duration.ofMinutes(30));
    }
}

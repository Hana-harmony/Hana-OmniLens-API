package com.hana.omnilens.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HannahAiPropertiesTest {

    @Test
    void defaultBaseUrlUsesInternalServiceName() {
        HannahAiProperties properties = new HannahAiProperties(null);

        assertThat(properties.baseUrl().toString()).isEqualTo("http://hannah-montana-ai:8000");
    }
}

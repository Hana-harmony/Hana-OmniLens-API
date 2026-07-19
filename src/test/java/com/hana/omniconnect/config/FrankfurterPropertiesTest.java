package com.hana.omniconnect.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FrankfurterPropertiesTest {

    @Test
    void defaultsUsePublicFrankfurterEndpoint() {
        FrankfurterProperties properties = new FrankfurterProperties(null);

        assertThat(properties.baseUrl().toString()).isEqualTo("https://api.frankfurter.dev");
    }
}

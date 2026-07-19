package com.hana.omniconnect.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class KrxOpenApiPropertiesTest {

    @Test
    void defaultsUseKrxOpenApiDataEndpointAndFailClosedWhenAuthKeyIsMissing() {
        KrxOpenApiProperties properties = new KrxOpenApiProperties(null, null);

        assertThat(properties.baseUrl().toString()).isEqualTo("https://data-dbg.krx.co.kr");
        assertThatThrownBy(properties::requiredAuthKey)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("omni-connect.providers.krx-open-api.auth-key");
    }
}

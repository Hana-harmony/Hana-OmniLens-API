package com.hana.omnilens.config;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Duration;

import org.junit.jupiter.api.Test;

class ApiSignaturePropertiesTest {

    @Test
    void defaultsAreBoundedAndUseRedis() {
        ApiSignatureProperties properties = new ApiSignatureProperties(false, null, null, 0);

        assertThat(properties.enabled()).isFalse();
        assertThat(properties.allowedClockSkew()).isEqualTo(Duration.ofMinutes(2));
        assertThat(properties.nonceStoreMode()).isEqualTo(ApiSignatureProperties.NonceStoreMode.REDIS);
        assertThat(properties.maxNonces()).isEqualTo(10_000);
    }
}

package com.hana.omnilens.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class ApiSignaturePropertiesTest {

    @Test
    void defaultsAreBoundedAndDisabled() {
        ApiSignatureProperties properties = new ApiSignatureProperties(false, null, null, 0);

        assertThat(properties.enabled()).isFalse();
        assertThat(properties.allowedClockSkew()).isEqualTo(Duration.ofMinutes(5));
        assertThat(properties.maxNonces()).isEqualTo(10_000);
        assertThatThrownBy(properties::requiredSecret)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("omnilens.security.signature.secret");
    }
}

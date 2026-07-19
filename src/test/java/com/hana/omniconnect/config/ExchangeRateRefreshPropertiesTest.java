package com.hana.omniconnect.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class ExchangeRateRefreshPropertiesTest {

    @Test
    void normalizesCurrenciesAndDefaultsUnsafeValues() {
        ExchangeRateRefreshProperties properties = new ExchangeRateRefreshProperties(
                true,
                -1,
                -3,
                List.of("usd", " USD ", "", "jpy"));

        assertThat(properties.enabled()).isTrue();
        assertThat(properties.fixedDelayMs()).isEqualTo(300_000L);
        assertThat(properties.baseDateOffsetDays()).isZero();
        assertThat(properties.currencies()).containsExactly("USD", "JPY");
    }

    @Test
    void nullCurrenciesBecomeEmptyList() {
        ExchangeRateRefreshProperties properties = new ExchangeRateRefreshProperties(false, 60_000L, 1, null);

        assertThat(properties.currencies()).isEmpty();
    }
}

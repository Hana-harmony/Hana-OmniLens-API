package com.hana.omniconnect.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class KisRealtimePropertiesTest {

    @Test
    void constructorDropsBlankStockCodes() {
        KisRealtimeProperties properties = new KisRealtimeProperties(true, List.of("005930", "", " ", "000660"));

        assertThat(properties.stockCodes()).containsExactly("005930", "000660");
    }
}

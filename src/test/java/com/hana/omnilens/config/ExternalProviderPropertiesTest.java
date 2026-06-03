package com.hana.omnilens.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ExternalProviderPropertiesTest {

    @Test
    void defaultsDoNotExposeSecretsAndFailClosedWhenRequired() {
        ExternalProviderProperties properties = new ExternalProviderProperties(null, null, null, null);

        assertThat(properties.publicData().stockSecuritiesBaseUrl().toString())
                .isEqualTo("https://apis.data.go.kr/1160100/service/GetStockSecuritiesInfoService");
        assertThat(properties.naverNews().baseUrl().toString()).isEqualTo("https://openapi.naver.com");
        assertThat(properties.openDart().baseUrl().toString()).isEqualTo("https://opendart.fss.or.kr");
        assertThat(properties.krx().baseUrl().toString()).isEqualTo("https://data.krx.co.kr");

        assertThatThrownBy(() -> properties.naverNews().requiredClientSecret())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("omnilens.providers.naver-news.client-secret");
    }
}

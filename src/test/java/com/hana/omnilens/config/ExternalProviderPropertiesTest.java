package com.hana.omnilens.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ExternalProviderPropertiesTest {

    @Test
    void defaultsDoNotExposeSecretsAndFailClosedWhenRequired() {
        ExternalProviderProperties properties =
                new ExternalProviderProperties(null, null, null, null, null, null);

        assertThat(properties.publicData().stockSecuritiesBaseUrl().toString())
                .isEqualTo("https://apis.data.go.kr/1160100/service/GetStockSecuritiesInfoService");
        assertThat(properties.naverNews().baseUrl().toString()).isEqualTo("https://openapi.naver.com");
        assertThat(properties.openDart().baseUrl().toString()).isEqualTo("https://opendart.fss.or.kr");
        assertThat(properties.kis().baseUrl().toString())
                .isEqualTo("https://openapivts.koreainvestment.com:29443");
        assertThat(properties.kis().websocketUrl().toString())
                .isEqualTo("ws://ops.koreainvestment.com:31000");
        assertThat(properties.deepLTranslation().baseUrl().toString()).isEqualTo("https://api-free.deepl.com");

        assertThatThrownBy(() -> properties.naverNews().requiredClientSecret())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("omnilens.providers.naver-news.client-secret");
        assertThatThrownBy(() -> properties.kis().requiredApprovalKey())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("omnilens.providers.kis.approval-key");
        assertThatThrownBy(() -> properties.deepLTranslation().requiredApiKey())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("omnilens.providers.deep-l-translation.api-key");
    }
}

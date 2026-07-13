package com.hana.omnilens.provider.market;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;

import org.junit.jupiter.api.Test;

import com.hana.omnilens.config.ExternalProviderProperties;

class KisProviderSupportTest {

    @Test
    void virtualTradingCredentialIsNotReusedForRealIndexProvider() {
        ExternalProviderProperties properties = properties(
                kis(
                        "https://openapivts.koreainvestment.com:29443",
                        "ws://ops.koreainvestment.com:31000",
                        "virtual-app-key",
                        "virtual-app-secret"),
                kis(
                        "https://openapi.koreainvestment.com:9443",
                        "ws://ops.koreainvestment.com:21000",
                        "",
                        ""));

        assertThat(KisProviderSupport.realIndexRestProvider(properties)).isEmpty();
        assertThat(KisProviderSupport.realIndexRealtimeProvider(properties)).isEmpty();
    }

    @Test
    void explicitRealCredentialEnablesRealIndexProvider() {
        ExternalProviderProperties properties = properties(
                kis(
                        "https://openapivts.koreainvestment.com:29443",
                        "ws://ops.koreainvestment.com:31000",
                        "virtual-app-key",
                        "virtual-app-secret"),
                kis(
                        "https://openapi.koreainvestment.com:9443",
                        "ws://ops.koreainvestment.com:21000",
                        "real-app-key",
                        "real-app-secret"));

        assertThat(KisProviderSupport.realIndexRestProvider(properties)).isPresent();
        assertThat(KisProviderSupport.realIndexRealtimeProvider(properties)).isPresent();
    }

    @Test
    void sameProviderIdentityIgnoresRotatingTokens() {
        ExternalProviderProperties.Kis first = new ExternalProviderProperties.Kis(
                URI.create("https://openapi.koreainvestment.com:9443"),
                URI.create("ws://ops.koreainvestment.com:21000"),
                "account",
                "app-key",
                "app-secret",
                "access-token-a",
                "approval-key-a");
        ExternalProviderProperties.Kis second = new ExternalProviderProperties.Kis(
                URI.create("https://openapi.koreainvestment.com:9443"),
                URI.create("ws://ops.koreainvestment.com:21000"),
                "account",
                "app-key",
                "app-secret",
                "access-token-b",
                "approval-key-b");

        assertThat(KisProviderSupport.isSameProvider(first, second)).isFalse();
        assertThat(KisProviderSupport.isSameRealtimeIdentity(first, second)).isTrue();
    }

    private ExternalProviderProperties properties(
            ExternalProviderProperties.Kis primary,
            ExternalProviderProperties.Kis real) {
        return new ExternalProviderProperties(null, null, null, null, primary, real);
    }

    private ExternalProviderProperties.Kis kis(
            String baseUrl,
            String websocketUrl,
            String appKey,
            String appSecret) {
        return new ExternalProviderProperties.Kis(
                URI.create(baseUrl),
                URI.create(websocketUrl),
                "",
                appKey,
                appSecret,
                "",
                "");
    }
}

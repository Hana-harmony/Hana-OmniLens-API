package com.hana.omnilens.config;

import java.net.URI;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "omnilens.providers")
public record ExternalProviderProperties(
        PublicData publicData,
        NaverNews naverNews,
        OpenDart openDart,
        Krx krx,
        Kis kis,
        DeepLTranslation deepLTranslation
) {

    @ConstructorBinding
    public ExternalProviderProperties {
        publicData = publicData == null ? PublicData.defaults() : publicData.withDefaults();
        naverNews = naverNews == null ? NaverNews.defaults() : naverNews.withDefaults();
        openDart = openDart == null ? OpenDart.defaults() : openDart.withDefaults();
        krx = krx == null ? Krx.defaults() : krx.withDefaults();
        kis = kis == null ? Kis.defaults() : kis.withDefaults();
        deepLTranslation = deepLTranslation == null ? DeepLTranslation.defaults() : deepLTranslation.withDefaults();
    }

    public record PublicData(URI stockSecuritiesBaseUrl, String serviceKey) {

        private static PublicData defaults() {
            return new PublicData(
                    URI.create("https://apis.data.go.kr/1160100/service/GetStockSecuritiesInfoService"),
                    "");
        }

        private PublicData withDefaults() {
            return new PublicData(
                    stockSecuritiesBaseUrl == null ? defaults().stockSecuritiesBaseUrl() : stockSecuritiesBaseUrl,
                    serviceKey == null ? "" : serviceKey);
        }

        public String requiredServiceKey() {
            return requireSecret(serviceKey, "omnilens.providers.public-data.service-key");
        }
    }

    public record NaverNews(URI baseUrl, String clientId, String clientSecret) {

        private static NaverNews defaults() {
            return new NaverNews(URI.create("https://openapi.naver.com"), "", "");
        }

        private NaverNews withDefaults() {
            return new NaverNews(
                    baseUrl == null ? defaults().baseUrl() : baseUrl,
                    clientId == null ? "" : clientId,
                    clientSecret == null ? "" : clientSecret);
        }

        public String requiredClientId() {
            return requireSecret(clientId, "omnilens.providers.naver-news.client-id");
        }

        public String requiredClientSecret() {
            return requireSecret(clientSecret, "omnilens.providers.naver-news.client-secret");
        }
    }

    public record OpenDart(URI baseUrl, String apiKey) {

        private static OpenDart defaults() {
            return new OpenDart(URI.create("https://opendart.fss.or.kr"), "");
        }

        private OpenDart withDefaults() {
            return new OpenDart(
                    baseUrl == null ? defaults().baseUrl() : baseUrl,
                    apiKey == null ? "" : apiKey);
        }

        public String requiredApiKey() {
            return requireSecret(apiKey, "omnilens.providers.open-dart.api-key");
        }
    }

    public record Krx(URI baseUrl) {

        private static Krx defaults() {
            return new Krx(URI.create("https://data.krx.co.kr"));
        }

        private Krx withDefaults() {
            return new Krx(baseUrl == null ? defaults().baseUrl() : baseUrl);
        }
    }

    public record Kis(
            URI baseUrl,
            URI websocketUrl,
            String appKey,
            String appSecret,
            String accessToken,
            String approvalKey) {

        private static Kis defaults() {
            return new Kis(
                    URI.create("https://openapivts.koreainvestment.com:29443"),
                    URI.create("ws://ops.koreainvestment.com:31000"),
                    "",
                    "",
                    "",
                    "");
        }

        private Kis withDefaults() {
            Kis defaults = defaults();
            return new Kis(
                    baseUrl == null ? defaults.baseUrl() : baseUrl,
                    websocketUrl == null ? defaults.websocketUrl() : websocketUrl,
                    appKey == null ? "" : appKey,
                    appSecret == null ? "" : appSecret,
                    accessToken == null ? "" : accessToken,
                    approvalKey == null ? "" : approvalKey);
        }

        public String requiredAppKey() {
            return requireSecret(appKey, "omnilens.providers.kis.app-key");
        }

        public String requiredAppSecret() {
            return requireSecret(appSecret, "omnilens.providers.kis.app-secret");
        }

        public String requiredAccessToken() {
            return requireSecret(accessToken, "omnilens.providers.kis.access-token");
        }

        public String requiredApprovalKey() {
            return requireSecret(approvalKey, "omnilens.providers.kis.approval-key");
        }
    }

    public record DeepLTranslation(URI baseUrl, String apiKey) {

        private static DeepLTranslation defaults() {
            return new DeepLTranslation(URI.create("https://api-free.deepl.com"), "");
        }

        private DeepLTranslation withDefaults() {
            return new DeepLTranslation(
                    baseUrl == null ? defaults().baseUrl() : baseUrl,
                    apiKey == null ? "" : apiKey);
        }

        public String requiredApiKey() {
            return requireSecret(apiKey, "omnilens.providers.deep-l-translation.api-key");
        }
    }

    private static String requireSecret(String value, String propertyName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(propertyName + " is not configured");
        }
        return value;
    }
}

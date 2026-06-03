package com.hana.omnilens.config;

import java.net.URI;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "omnilens.providers")
public record ExternalProviderProperties(
        PublicData publicData,
        NaverNews naverNews,
        OpenDart openDart
) {

    public ExternalProviderProperties {
        publicData = publicData == null ? PublicData.defaults() : publicData.withDefaults();
        naverNews = naverNews == null ? NaverNews.defaults() : naverNews.withDefaults();
        openDart = openDart == null ? OpenDart.defaults() : openDart.withDefaults();
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

    private static String requireSecret(String value, String propertyName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(propertyName + " is not configured");
        }
        return value;
    }
}

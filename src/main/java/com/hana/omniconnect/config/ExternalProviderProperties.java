package com.hana.omniconnect.config;

import java.net.URI;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "omni-connect.providers")
public record ExternalProviderProperties(
        PublicData publicData,
        NaverNews naverNews,
        OpenDart openDart,
        Krx krx,
        Kis kis,
        Kis realKis
) {

    @ConstructorBinding
    public ExternalProviderProperties {
        publicData = publicData == null ? PublicData.defaults() : publicData.withDefaults();
        naverNews = naverNews == null ? NaverNews.defaults() : naverNews.withDefaults();
        openDart = openDart == null ? OpenDart.defaults() : openDart.withDefaults();
        krx = krx == null ? Krx.defaults() : krx.withDefaults();
        kis = kis == null ? Kis.virtualDefaults() : kis.withDefaults(Kis.virtualDefaults());
        realKis = realKis == null ? Kis.realDefaults() : realKis.withDefaults(Kis.realDefaults());
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
            return requireSecret(serviceKey, "omni-connect.providers.public-data.service-key");
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
            return requireSecret(clientId, "omni-connect.providers.naver-news.client-id");
        }

        public String requiredClientSecret() {
            return requireSecret(clientSecret, "omni-connect.providers.naver-news.client-secret");
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
            return requireSecret(apiKey, "omni-connect.providers.open-dart.api-key");
        }
    }

    public record Krx(
            URI baseUrl,
            boolean scrapingEnabled,
            String id,
            String password,
            String loginPath,
            String foreignOwnershipHistoryBld) {

        private static Krx defaults() {
            return new Krx(
                    URI.create("https://data.krx.co.kr"),
                    true,
                    "",
                    "",
                    "/contents/MDC/COMS/client/MDCCOMS001D1.cmd",
                    "dbms/MDC/STAT/standard/MDCSTAT03702");
        }

        private Krx withDefaults() {
            Krx defaults = defaults();
            return new Krx(
                    baseUrl == null ? defaults.baseUrl() : baseUrl,
                    scrapingEnabled,
                    id == null ? "" : id,
                    password == null ? "" : password,
                    StringUtils.hasText(loginPath) ? loginPath : defaults.loginPath(),
                    StringUtils.hasText(foreignOwnershipHistoryBld)
                            ? foreignOwnershipHistoryBld
                            : defaults.foreignOwnershipHistoryBld());
        }

        public String requiredId() {
            return requireSecret(id, "omni-connect.providers.krx.id");
        }

        public String requiredPassword() {
            return requireSecret(password, "omni-connect.providers.krx.password");
        }
    }

    public record Kis(
            URI baseUrl,
            URI websocketUrl,
            String accountNumber,
            String appKey,
            String appSecret,
            String accessToken,
            String approvalKey) {

        private static Kis virtualDefaults() {
            return new Kis(
                    URI.create("https://openapivts.koreainvestment.com:29443"),
                    URI.create("ws://ops.koreainvestment.com:31000"),
                    "",
                    "",
                    "",
                    "",
                    "");
        }

        private static Kis realDefaults() {
            return new Kis(
                    URI.create("https://openapi.koreainvestment.com:9443"),
                    URI.create("ws://ops.koreainvestment.com:21000"),
                    "",
                    "",
                    "",
                    "",
                    "");
        }

        private Kis withDefaults(Kis defaults) {
            return new Kis(
                    baseUrl == null ? defaults.baseUrl() : baseUrl,
                    websocketUrl == null ? defaults.websocketUrl() : websocketUrl,
                    accountNumber == null ? "" : accountNumber,
                    appKey == null ? "" : appKey,
                    appSecret == null ? "" : appSecret,
                    accessToken == null ? "" : accessToken,
                    approvalKey == null ? "" : approvalKey);
        }

        public String requiredAppKey() {
            return requireSecret(appKey, "omni-connect.providers.kis.app-key");
        }

        public String requiredAppSecret() {
            return requireSecret(appSecret, "omni-connect.providers.kis.app-secret");
        }

        public String requiredAccessToken() {
            return requireSecret(accessToken, "omni-connect.providers.kis.access-token");
        }

        public String requiredApprovalKey() {
            return requireSecret(approvalKey, "omni-connect.providers.kis.approval-key");
        }
    }

    private static String requireSecret(String value, String propertyName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(propertyName + " is not configured");
        }
        return value;
    }
}

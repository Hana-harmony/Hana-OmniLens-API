package com.hana.omnilens.provider.market;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.hana.omnilens.config.ExternalProviderProperties;
import com.hana.omnilens.provider.ExternalProviderResiliencePolicy;

@Component
public class KisIndexCurrentPriceClient {

    private static final String INDEX_CURRENT_PRICE_TR_ID = "FHPUP02100000";
    private static final String SOURCE = "KIS_INDEX_CURRENT_PRICE";
    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    private final RestClient restClient;
    private final ExternalProviderProperties.Kis kisProperties;
    private final KisAccessTokenProvider accessTokenProvider;
    private final ExternalProviderResiliencePolicy resiliencePolicy;
    private final Clock clock;

    @Autowired
    public KisIndexCurrentPriceClient(
            RestClient.Builder restClientBuilder,
            ExternalProviderProperties properties,
            KisAccessTokenProvider accessTokenProvider,
            ExternalProviderResiliencePolicy resiliencePolicy) {
        this(restClientBuilder, properties, accessTokenProvider, resiliencePolicy, Clock.system(KOREA_ZONE));
    }

    KisIndexCurrentPriceClient(
            RestClient.Builder restClientBuilder,
            ExternalProviderProperties properties,
            KisAccessTokenProvider accessTokenProvider,
            ExternalProviderResiliencePolicy resiliencePolicy,
            Clock clock) {
        Optional<ExternalProviderProperties.Kis> indexProvider = KisProviderSupport.realIndexRestProvider(properties);
        this.kisProperties = indexProvider.orElse(null);
        this.restClient = indexProvider
                .map(provider -> restClientBuilder
                        .baseUrl(provider.baseUrl().toString())
                        .build())
                .orElse(null);
        this.accessTokenProvider = indexProvider
                .map(provider -> KisProviderSupport.isSameProvider(provider, properties.kis())
                        ? accessTokenProvider
                        : new KisAccessTokenProvider(restClientBuilder, provider, resiliencePolicy, clock))
                .orElse(null);
        this.resiliencePolicy = resiliencePolicy;
        this.clock = clock;
    }

    public Optional<KisIndexCurrentPriceSnapshot> findCurrentIndex(String indexCode) {
        if (kisProperties == null || restClient == null || accessTokenProvider == null) {
            return Optional.empty();
        }
        String accessToken = accessTokenProvider.accessToken();
        String appKey = kisProperties.requiredAppKey();
        String appSecret = kisProperties.requiredAppSecret();
        JsonNode root = resiliencePolicy.execute("kis-index-current-price", () -> restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/uapi/domestic-stock/v1/quotations/inquire-index-price")
                        .queryParam("FID_COND_MRKT_DIV_CODE", "U")
                        .queryParam("FID_INPUT_ISCD", indexCode)
                        .build())
                .header("Content-Type", "application/json; charset=utf-8")
                .header("authorization", "Bearer " + accessToken)
                .header("appkey", appKey)
                .header("appsecret", appSecret)
                .header("tr_id", INDEX_CURRENT_PRICE_TR_ID)
                .retrieve()
                .body(JsonNode.class));

        JsonNode output = root == null ? null : root.path("output");
        if (output == null || output.isMissingNode() || output.isNull()) {
            return Optional.empty();
        }

        BigDecimal currentValue = firstDecimal(output, "bstp_nmix_prpr", "stck_prpr");
        if (currentValue == null) {
            return Optional.empty();
        }

        String changeSign = firstText(output, "prdy_vrss_sign", "prdy_vrss_sign_name");
        BigDecimal changeValue = signed(
                firstDecimal(output, "bstp_nmix_prdy_vrss", "prdy_vrss"),
                changeSign);
        BigDecimal changeRate = signed(
                firstDecimal(output, "bstp_nmix_prdy_ctrt", "prdy_ctrt"),
                changeSign);

        return Optional.of(new KisIndexCurrentPriceSnapshot(
                indexCode,
                indexName(indexCode, output),
                market(indexCode),
                currentValue,
                normalizeChangeSign(changeSign),
                zeroIfNull(changeValue),
                zeroIfNull(changeRate),
                firstLong(output, "acml_vol", "cntg_vol"),
                firstLong(output, "acml_tr_pbmn", "acml_tr_pbmn"),
                zeroIfNull(firstDecimal(output, "bstp_nmix_oprc", "stck_oprc")),
                zeroIfNull(firstDecimal(output, "bstp_nmix_hgpr", "stck_hgpr")),
                zeroIfNull(firstDecimal(output, "bstp_nmix_lwpr", "stck_lwpr")),
                Instant.now(clock),
                SOURCE));
    }

    private static String indexName(String indexCode, JsonNode output) {
        String name = firstText(output, "hts_kor_isnm", "bstp_kor_isnm", "bstp_nmix_prpr_name");
        if (!name.isBlank()) {
            return name;
        }
        return switch (indexCode) {
            case "1001" -> "KOSDAQ";
            case "2001" -> "KOSPI 200";
            default -> "KOSPI";
        };
    }

    private static String market(String indexCode) {
        return switch (indexCode) {
            case "1001" -> "KOSDAQ";
            case "2001" -> "KOSPI200";
            default -> "KOSPI";
        };
    }

    private static String normalizeChangeSign(String changeSign) {
        return changeSign == null || changeSign.isBlank() ? "3" : changeSign.trim();
    }

    private static BigDecimal signed(BigDecimal value, String changeSign) {
        if (value == null) {
            return null;
        }
        BigDecimal absolute = value.abs();
        return switch (normalizeChangeSign(changeSign)) {
            case "4", "5" -> absolute.negate();
            case "1", "2" -> absolute;
            default -> BigDecimal.ZERO;
        };
    }

    private static BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static long firstLong(JsonNode output, String... fieldNames) {
        for (String fieldName : fieldNames) {
            String normalized = normalizeNumeric(output.path(fieldName).asText());
            if (!normalized.isBlank() && !"-".equals(normalized)) {
                return Long.parseLong(normalized);
            }
        }
        return 0L;
    }

    private static BigDecimal firstDecimal(JsonNode output, String... fieldNames) {
        for (String fieldName : fieldNames) {
            String normalized = normalizeNumeric(output.path(fieldName).asText());
            if (!normalized.isBlank() && !"-".equals(normalized)) {
                return new BigDecimal(normalized);
            }
        }
        return null;
    }

    private static String firstText(JsonNode output, String... fieldNames) {
        for (String fieldName : fieldNames) {
            String value = output.path(fieldName).asText("");
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private static String normalizeNumeric(String value) {
        if (value == null) {
            return "";
        }
        return value.replace(",", "").replace("%", "").trim();
    }
}

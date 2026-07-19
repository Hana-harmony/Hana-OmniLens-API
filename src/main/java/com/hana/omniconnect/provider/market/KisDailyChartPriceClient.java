package com.hana.omniconnect.provider.market;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.StreamSupport;

import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.hana.omniconnect.config.ExternalProviderProperties;
import com.hana.omniconnect.provider.ExternalProviderResiliencePolicy;

@Component
public class KisDailyChartPriceClient {

    private static final DateTimeFormatter KIS_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final String DAILY_ITEM_CHART_PRICE_TR_ID = "FHKST03010100";

    private final RestClient restClient;
    private final ExternalProviderProperties.Kis kisProperties;
    private final KisAccessTokenProvider accessTokenProvider;
    private final ExternalProviderResiliencePolicy resiliencePolicy;

    public KisDailyChartPriceClient(
            RestClient.Builder restClientBuilder,
            ExternalProviderProperties properties,
            KisAccessTokenProvider accessTokenProvider,
            ExternalProviderResiliencePolicy resiliencePolicy) {
        this.restClient = restClientBuilder
                .baseUrl(properties.kis().baseUrl().toString())
                .build();
        this.kisProperties = properties.kis();
        this.accessTokenProvider = accessTokenProvider;
        this.resiliencePolicy = resiliencePolicy;
    }

    public List<KisDailyChartPrice> findDailyPrices(String stockCode, LocalDate from, LocalDate to) {
        String accessToken = accessTokenProvider.accessToken();
        String appKey = kisProperties.requiredAppKey();
        String appSecret = kisProperties.requiredAppSecret();
        JsonNode root = resiliencePolicy.execute("kis-daily-item-chart-price", () -> restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/uapi/domestic-stock/v1/quotations/inquire-daily-itemchartprice")
                        .queryParam("FID_COND_MRKT_DIV_CODE", "J")
                        .queryParam("FID_INPUT_ISCD", stockCode)
                        .queryParam("FID_INPUT_DATE_1", KIS_DATE.format(from))
                        .queryParam("FID_INPUT_DATE_2", KIS_DATE.format(to))
                        .queryParam("FID_PERIOD_DIV_CODE", "D")
                        .queryParam("FID_ORG_ADJ_PRC", "0")
                        .build())
                .header("Content-Type", "application/json; charset=utf-8")
                .header("authorization", "Bearer " + accessToken)
                .header("appkey", appKey)
                .header("appsecret", appSecret)
                .header("tr_id", DAILY_ITEM_CHART_PRICE_TR_ID)
                .retrieve()
                .body(JsonNode.class));

        JsonNode items = root == null ? null : root.path("output2");
        if (items == null || !items.isArray()) {
            return List.of();
        }
        return StreamSupport.stream(items.spliterator(), false)
                .map(KisDailyChartPriceClient::toDailyPrice)
                .toList();
    }

    private static KisDailyChartPrice toDailyPrice(JsonNode item) {
        return new KisDailyChartPrice(
                LocalDate.parse(item.path("stck_bsop_date").asText(), KIS_DATE),
                parseDecimal(item.path("stck_oprc").asText()),
                parseDecimal(item.path("stck_hgpr").asText()),
                parseDecimal(item.path("stck_lwpr").asText()),
                parseDecimal(item.path("stck_clpr").asText()),
                parseDecimal(item.path("prdy_ctrt").asText()),
                parseLong(item.path("acml_vol").asText()),
                parseDecimal(item.path("acml_tr_pbmn").asText()));
    }

    private static long parseLong(String value) {
        String normalized = normalizeNumeric(value);
        if (normalized.isBlank()) {
            return 0L;
        }
        return Long.parseLong(normalized);
    }

    private static BigDecimal parseDecimal(String value) {
        String normalized = normalizeNumeric(value);
        if (normalized.isBlank() || "-".equals(normalized)) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(normalized);
    }

    private static String normalizeNumeric(String value) {
        if (value == null) {
            return "";
        }
        return value.replace(",", "").replace("%", "").trim();
    }
}

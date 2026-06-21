package com.hana.omnilens.provider.market;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.hana.omnilens.config.ExternalProviderProperties;
import com.hana.omnilens.provider.ExternalProviderResiliencePolicy;

@Component
public class KisRestOrderBookClient {

    private static final String DOMESTIC_STOCK_ORDERBOOK_TR_ID = "FHKST01010200";
    private static final int ORDERBOOK_LEVEL_COUNT = 10;

    private final RestClient restClient;
    private final ExternalProviderProperties.Kis kisProperties;
    private final KisAccessTokenProvider accessTokenProvider;
    private final ExternalProviderResiliencePolicy resiliencePolicy;

    public KisRestOrderBookClient(
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

    public Optional<KisRestOrderBookSnapshot> findOrderBook(String stockCode) {
        String accessToken = accessTokenProvider.accessToken();
        String appKey = kisProperties.requiredAppKey();
        String appSecret = kisProperties.requiredAppSecret();
        JsonNode root = resiliencePolicy.execute("kis-rest-orderbook", () -> restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/uapi/domestic-stock/v1/quotations/inquire-asking-price-exp-ccn")
                        .queryParam("FID_COND_MRKT_DIV_CODE", "J")
                        .queryParam("FID_INPUT_ISCD", stockCode)
                        .build())
                .header("Content-Type", "application/json; charset=utf-8")
                .header("authorization", "Bearer " + accessToken)
                .header("appkey", appKey)
                .header("appsecret", appSecret)
                .header("tr_id", DOMESTIC_STOCK_ORDERBOOK_TR_ID)
                .retrieve()
                .body(JsonNode.class));

        JsonNode output = root == null ? null : root.path("output1");
        if (output == null || output.isMissingNode() || output.isNull()) {
            return Optional.empty();
        }
        List<KisRestOrderBookSnapshot.Level> asks = levels(output, "askp", "askp_rsqn");
        List<KisRestOrderBookSnapshot.Level> bids = levels(output, "bidp", "bidp_rsqn");
        if (asks.isEmpty() && bids.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new KisRestOrderBookSnapshot(stockCode, asks, bids));
    }

    private static List<KisRestOrderBookSnapshot.Level> levels(JsonNode output, String pricePrefix, String quantityPrefix) {
        List<KisRestOrderBookSnapshot.Level> levels = new ArrayList<>(ORDERBOOK_LEVEL_COUNT);
        for (int index = 1; index <= ORDERBOOK_LEVEL_COUNT; index++) {
            BigDecimal price = parseDecimal(output.path(pricePrefix + index).asText(""));
            if (price == null || price.signum() <= 0) {
                continue;
            }
            levels.add(new KisRestOrderBookSnapshot.Level(
                    price,
                    parseLong(output.path(quantityPrefix + index).asText(""))));
        }
        return List.copyOf(levels);
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
        if (normalized.isBlank()) {
            return null;
        }
        return new BigDecimal(normalized);
    }

    private static String normalizeNumeric(String value) {
        if (value == null) {
            return "";
        }
        return value.replace(",", "").trim();
    }
}

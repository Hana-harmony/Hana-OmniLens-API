package com.hana.omnilens.provider.market;

import java.math.BigDecimal;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.hana.omnilens.config.ExternalProviderProperties;
import com.hana.omnilens.provider.ExternalProviderResiliencePolicy;

@Component
public class KisCurrentPriceClient {

    private static final String DOMESTIC_STOCK_CURRENT_PRICE_TR_ID = "FHKST01010100";

    private final RestClient restClient;
    private final ExternalProviderProperties.Kis kisProperties;
    private final KisAccessTokenProvider accessTokenProvider;
    private final ExternalProviderResiliencePolicy resiliencePolicy;

    public KisCurrentPriceClient(
            RestClient.Builder restClientBuilder,
            ExternalProviderProperties properties,
            ExternalProviderResiliencePolicy resiliencePolicy) {
        this.restClient = restClientBuilder
                .baseUrl(properties.kis().baseUrl().toString())
                .build();
        this.kisProperties = properties.kis();
        this.accessTokenProvider = new KisAccessTokenProvider(restClientBuilder, this.kisProperties, resiliencePolicy);
        this.resiliencePolicy = resiliencePolicy;
    }

    public Optional<KisCurrentPriceSnapshot> findCurrentPrice(String stockCode) {
        String accessToken = accessTokenProvider.accessToken();
        String appKey = kisProperties.requiredAppKey();
        String appSecret = kisProperties.requiredAppSecret();
        JsonNode root = resiliencePolicy.execute("kis-current-price", () -> restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/uapi/domestic-stock/v1/quotations/inquire-price")
                        .queryParam("FID_COND_MRKT_DIV_CODE", "J")
                        .queryParam("FID_INPUT_ISCD", stockCode)
                        .build())
                .header("Content-Type", "application/json; charset=utf-8")
                .header("authorization", "Bearer " + accessToken)
                .header("appkey", appKey)
                .header("appsecret", appSecret)
                .header("tr_id", DOMESTIC_STOCK_CURRENT_PRICE_TR_ID)
                .retrieve()
                .body(JsonNode.class));

        JsonNode output = root == null ? null : root.path("output");
        if (output == null || output.isMissingNode() || output.isNull()) {
            return Optional.empty();
        }

        String currentPrice = output.path("stck_prpr").asText("");
        if (currentPrice.isBlank()) {
            return Optional.empty();
        }

        return Optional.of(new KisCurrentPriceSnapshot(
                stockCode,
                output.path("hts_kor_isnm").asText(""),
                new BigDecimal(currentPrice),
                new BigDecimal(output.path("prdy_ctrt").asText("0")),
                output.path("acml_vol").asLong()));
    }
}

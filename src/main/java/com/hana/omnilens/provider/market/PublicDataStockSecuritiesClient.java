package com.hana.omnilens.provider.market;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.stream.StreamSupport;

import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.hana.omnilens.config.ExternalProviderProperties;

@Component
public class PublicDataStockSecuritiesClient {

    private static final DateTimeFormatter PUBLIC_DATA_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final RestClient restClient;
    private final ExternalProviderProperties.PublicData properties;

    public PublicDataStockSecuritiesClient(RestClient.Builder restClientBuilder, ExternalProviderProperties properties) {
        this.restClient = restClientBuilder
                .baseUrl(properties.publicData().stockSecuritiesBaseUrl().toString())
                .build();
        this.properties = properties.publicData();
    }

    public Optional<PublicDataStockPriceSnapshot> findPrice(String stockCode, LocalDate baseDate) {
        JsonNode root = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/getStockPriceInfo")
                        .queryParam("serviceKey", properties.requiredServiceKey())
                        .queryParam("resultType", "json")
                        .queryParam("numOfRows", 1)
                        .queryParam("pageNo", 1)
                        .queryParam("likeSrtnCd", stockCode)
                        .queryParam("basDt", PUBLIC_DATA_DATE.format(baseDate))
                        .build())
                .retrieve()
                .body(JsonNode.class);

        JsonNode items = root == null ? null : root.path("response").path("body").path("items").path("item");
        if (items == null || !items.isArray()) {
            return Optional.empty();
        }

        return StreamSupport.stream(items.spliterator(), false)
                .findFirst()
                .map(this::toSnapshot);
    }

    private PublicDataStockPriceSnapshot toSnapshot(JsonNode item) {
        return new PublicDataStockPriceSnapshot(
                item.path("srtnCd").asText(),
                item.path("itmsNm").asText(),
                item.path("mrktCtg").asText(),
                new BigDecimal(item.path("clpr").asText("0")),
                new BigDecimal(item.path("fltRt").asText("0")),
                item.path("trqu").asLong(),
                LocalDate.parse(item.path("basDt").asText(), PUBLIC_DATA_DATE));
    }
}

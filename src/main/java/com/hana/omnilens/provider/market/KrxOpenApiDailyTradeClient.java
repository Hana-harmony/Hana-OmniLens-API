package com.hana.omnilens.provider.market;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.StreamSupport;

import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.hana.omnilens.config.KrxOpenApiProperties;
import com.hana.omnilens.provider.ExternalProviderResiliencePolicy;

@Component
public class KrxOpenApiDailyTradeClient {

    private static final DateTimeFormatter KRX_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final RestClient restClient;
    private final KrxOpenApiProperties properties;
    private final ExternalProviderResiliencePolicy resiliencePolicy;

    public KrxOpenApiDailyTradeClient(
            RestClient.Builder restClientBuilder,
            KrxOpenApiProperties properties,
            ExternalProviderResiliencePolicy resiliencePolicy) {
        this.restClient = restClientBuilder
                .baseUrl(properties.baseUrl().toString())
                .build();
        this.properties = properties;
        this.resiliencePolicy = resiliencePolicy;
    }

    public List<KrxOpenApiDailyTrade> findKospiDailyTrades(LocalDate baseDate) {
        JsonNode root = resiliencePolicy.execute("krx-open-api-kospi-daily-trades", () -> restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/svc/apis/sto/stk_bydd_trd")
                        .queryParam("basDd", KRX_DATE.format(baseDate))
                        .build())
                .header("AUTH_KEY", properties.requiredAuthKey())
                .retrieve()
                .body(JsonNode.class));

        JsonNode items = root == null ? null : root.path("OutBlock_1");
        if (items == null || !items.isArray()) {
            return List.of();
        }
        return StreamSupport.stream(items.spliterator(), false)
                .map(this::toDailyTrade)
                .toList();
    }

    private KrxOpenApiDailyTrade toDailyTrade(JsonNode item) {
        return new KrxOpenApiDailyTrade(
                parseDate(item.path("BAS_DD").asText()),
                item.path("ISU_CD").asText(),
                item.path("ISU_SRT_CD").asText(),
                item.path("ISU_NM").asText(),
                item.path("MKT_NM").asText(),
                parseDecimal(item.path("TDD_CLSPRC").asText()),
                parseDecimal(item.path("FLUC_RT").asText()),
                parseLong(item.path("ACC_TRDVOL").asText()));
    }

    private static LocalDate parseDate(String value) {
        return LocalDate.parse(value, KRX_DATE);
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

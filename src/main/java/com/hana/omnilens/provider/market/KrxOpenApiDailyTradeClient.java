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

    public List<KrxOpenApiDailyTrade> findAllDailyTrades(LocalDate baseDate) {
        return List.of(
                        findDailyTrades("KOSPI", "/svc/apis/sto/stk_bydd_trd", baseDate),
                        findDailyTrades("KOSDAQ", "/svc/apis/sto/ksq_bydd_trd", baseDate),
                        findDailyTrades("KONEX", "/svc/apis/sto/knx_bydd_trd", baseDate))
                .stream()
                .flatMap(List::stream)
                .toList();
    }

    public List<KrxOpenApiDailyTrade> findKospiDailyTrades(LocalDate baseDate) {
        return findDailyTrades("KOSPI", "/svc/apis/sto/stk_bydd_trd", baseDate);
    }

    public List<KrxOpenApiDailyTrade> findDailyTrades(String market, LocalDate baseDate) {
        return switch (market) {
            case "KOSPI" -> findDailyTrades("KOSPI", "/svc/apis/sto/stk_bydd_trd", baseDate);
            case "KOSDAQ" -> findDailyTrades("KOSDAQ", "/svc/apis/sto/ksq_bydd_trd", baseDate);
            case "KONEX" -> findDailyTrades("KONEX", "/svc/apis/sto/knx_bydd_trd", baseDate);
            default -> throw new IllegalArgumentException("Unsupported KRX market: " + market);
        };
    }

    private List<KrxOpenApiDailyTrade> findDailyTrades(String market, String path, LocalDate baseDate) {
        JsonNode root = resiliencePolicy.execute("krx-open-api-kospi-daily-trades", () -> restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(path)
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
                .map(item -> toDailyTrade(item, market))
                .toList();
    }

    private KrxOpenApiDailyTrade toDailyTrade(JsonNode item, String fallbackMarket) {
        return new KrxOpenApiDailyTrade(
                parseDate(item.path("BAS_DD").asText()),
                item.path("ISU_CD").asText(),
                item.path("ISU_SRT_CD").asText(),
                item.path("ISU_NM").asText(),
                item.path("MKT_NM").asText(fallbackMarket),
                parseDecimal(item.path("TDD_OPNPRC").asText()),
                parseDecimal(item.path("TDD_HGPRC").asText()),
                parseDecimal(item.path("TDD_LWPRC").asText()),
                parseDecimal(item.path("TDD_CLSPRC").asText()),
                parseDecimal(item.path("FLUC_RT").asText()),
                parseLong(item.path("ACC_TRDVOL").asText()),
                parseDecimal(item.path("ACC_TRDVAL").asText()));
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

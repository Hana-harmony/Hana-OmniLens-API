package com.hana.omnilens.provider.market;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.stream.StreamSupport;

import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import com.hana.omnilens.config.ExternalProviderProperties;
import com.hana.omnilens.provider.ExternalProviderResiliencePolicy;

@Component
public class KrxForeignOwnershipClient {

    private static final DateTimeFormatter KRX_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final String FOREIGN_OWNERSHIP_BLD = "dbms/MDC/STAT/standard/MDCSTAT03702";

    private final RestClient restClient;
    private final ExternalProviderResiliencePolicy resiliencePolicy;

    public KrxForeignOwnershipClient(
            RestClient.Builder restClientBuilder,
            ExternalProviderProperties properties,
            ExternalProviderResiliencePolicy resiliencePolicy) {
        this.restClient = restClientBuilder
                .baseUrl(properties.krx().baseUrl().toString())
                .build();
        this.resiliencePolicy = resiliencePolicy;
    }

    public Optional<KrxForeignOwnershipSnapshot> findForeignOwnership(
            String stockCode,
            String stockName,
            String isinCode,
            LocalDate baseDate) {
        JsonNode root = resiliencePolicy.execute("krx-foreign-ownership", () -> restClient.post()
                .uri("/comm/bldAttendant/getJsonData.cmd")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .header("Accept", MediaType.APPLICATION_JSON_VALUE)
                .header("Origin", "https://data.krx.co.kr")
                .header("Referer", "https://data.krx.co.kr/contents/MDC/STAT/standard/MDCSTAT037.jsp")
                .header("X-Requested-With", "XMLHttpRequest")
                .body(KrxForeignOwnershipForm.of(stockCode, stockName, isinCode, baseDate))
                .retrieve()
                .body(JsonNode.class));

        JsonNode output = root == null ? null : root.path("output");
        if (output == null || !output.isArray()) {
            return Optional.empty();
        }

        return StreamSupport.stream(output.spliterator(), false)
                .filter(item -> stockCode.equals(item.path("ISU_SRT_CD").asText()))
                .findFirst()
                .map(item -> toSnapshot(stockCode, baseDate, item));
    }

    private KrxForeignOwnershipSnapshot toSnapshot(String stockCode, LocalDate baseDate, JsonNode item) {
        return new KrxForeignOwnershipSnapshot(
                stockCode,
                parseLong(item.path("FORN_HD_QTY").asText()),
                parseDecimal(item.path("FORN_SHR_RT").asText()),
                parseLong(item.path("FORN_ORD_LMT_QTY").asText()),
                parseDecimal(item.path("FORN_ORD_LMT_RT").asText()),
                baseDate);
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

    private static class KrxForeignOwnershipForm {

        private static MultiValueMap<String, String> of(
                String stockCode,
                String stockName,
                String isinCode,
                LocalDate baseDate) {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("bld", FOREIGN_OWNERSHIP_BLD);
            form.add("locale", "ko_KR");
            form.add("searchType", "2");
            form.add("mktId", "ALL");
            form.add("segTpCd", "ALL");
            form.add("trdDd", KRX_DATE.format(baseDate));
            form.add("isuCd", isinCode);
            form.add("isuCd2", isinCode);
            form.add("tboxisuCd_finder_stkisu0", stockCode + "/" + stockName);
            form.add("codeNmisuCd_finder_stkisu0", stockName);
            form.add("param1isuCd_finder_stkisu0", "ALL");
            form.add("share", "1");
            form.add("money", "1");
            return form;
        }
    }
}

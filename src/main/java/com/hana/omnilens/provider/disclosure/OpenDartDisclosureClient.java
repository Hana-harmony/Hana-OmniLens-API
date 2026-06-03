package com.hana.omnilens.provider.disclosure;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.StreamSupport;

import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.hana.omnilens.config.ExternalProviderProperties;

@Component
public class OpenDartDisclosureClient {

    private static final DateTimeFormatter DART_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final RestClient restClient;
    private final ExternalProviderProperties.OpenDart properties;

    public OpenDartDisclosureClient(RestClient.Builder restClientBuilder, ExternalProviderProperties properties) {
        this.restClient = restClientBuilder
                .baseUrl(properties.openDart().baseUrl().toString())
                .build();
        this.properties = properties.openDart();
    }

    public List<OpenDartDisclosure> search(String corpCode, LocalDate beginDate, LocalDate endDate) {
        JsonNode root = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/list.json")
                        .queryParam("crtfc_key", properties.requiredApiKey())
                        .queryParam("corp_code", corpCode)
                        .queryParam("bgn_de", DART_DATE.format(beginDate))
                        .queryParam("end_de", DART_DATE.format(endDate))
                        .queryParam("page_no", 1)
                        .queryParam("page_count", 100)
                        .build())
                .retrieve()
                .body(JsonNode.class);

        JsonNode list = root == null ? null : root.path("list");
        if (list == null || !list.isArray()) {
            return List.of();
        }

        return StreamSupport.stream(list.spliterator(), false)
                .map(this::toDisclosure)
                .toList();
    }

    private OpenDartDisclosure toDisclosure(JsonNode item) {
        String receiptNumber = item.path("rcept_no").asText();
        return new OpenDartDisclosure(
                receiptNumber,
                item.path("corp_name").asText(),
                item.path("report_nm").asText(),
                LocalDate.parse(item.path("rcept_dt").asText(), DART_DATE),
                "https://dart.fss.or.kr/dsaf001/main.do?rcpNo=" + receiptNumber);
    }
}

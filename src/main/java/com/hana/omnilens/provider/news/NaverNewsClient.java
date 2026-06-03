package com.hana.omnilens.provider.news;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.HtmlUtils;

import com.hana.omnilens.config.ExternalProviderProperties;

@Component
public class NaverNewsClient {

    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");

    private final RestClient restClient;
    private final ExternalProviderProperties.NaverNews properties;

    public NaverNewsClient(RestClient.Builder restClientBuilder, ExternalProviderProperties properties) {
        this.restClient = restClientBuilder
                .baseUrl(properties.naverNews().baseUrl().toString())
                .build();
        this.properties = properties.naverNews();
    }

    public List<NaverNewsArticle> search(String query, int display) {
        NaverNewsResponse response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v1/search/news.json")
                        .queryParam("query", query)
                        .queryParam("display", display)
                        .queryParam("sort", "date")
                        .build())
                .header("X-Naver-Client-Id", properties.requiredClientId())
                .header("X-Naver-Client-Secret", properties.requiredClientSecret())
                .retrieve()
                .body(NaverNewsResponse.class);

        if (response == null || response.items() == null) {
            return List.of();
        }

        return response.items().stream()
                .map(item -> new NaverNewsArticle(
                        clean(item.title()),
                        clean(item.description()),
                        item.originallink() == null ? item.link() : item.originallink(),
                        parsePublishedAt(item.pubDate())))
                .toList();
    }

    private static String clean(String value) {
        if (value == null) {
            return "";
        }
        String withoutTags = HTML_TAG_PATTERN.matcher(value).replaceAll("");
        return HtmlUtils.htmlUnescape(withoutTags).trim();
    }

    private static Instant parsePublishedAt(String value) {
        if (value == null || value.isBlank()) {
            return Instant.EPOCH;
        }
        try {
            return ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
        } catch (DateTimeParseException exception) {
            return Instant.EPOCH;
        }
    }

    private record NaverNewsResponse(List<NaverNewsItem> items) {
    }

    private record NaverNewsItem(
            String title,
            String originallink,
            String link,
            String description,
            @JsonProperty("pubDate") String pubDate
    ) {
    }
}

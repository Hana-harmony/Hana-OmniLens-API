package com.hana.omniconnect.provider.news;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;
import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.HtmlUtils;

import com.hana.omniconnect.config.ExternalProviderProperties;
import com.hana.omniconnect.provider.ExternalProviderResiliencePolicy;

@Component
public class NaverNewsClient {

    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");

    private final RestClient restClient;
    private final ExternalProviderProperties.NaverNews properties;
    private final ExternalProviderResiliencePolicy resiliencePolicy;
    private final NaverNewsRequestBudget requestBudget;
    private final Map<String, CachedSearch> queryCache = new ConcurrentHashMap<>();
    private final Object[] queryLocks = IntStream.range(0, 64).mapToObj(ignored -> new Object()).toArray();

    public NaverNewsClient(
            RestClient.Builder restClientBuilder,
            ExternalProviderProperties properties,
            ExternalProviderResiliencePolicy resiliencePolicy,
            NaverNewsRequestBudget requestBudget) {
        this.restClient = restClientBuilder
                .baseUrl(properties.naverNews().baseUrl().toString())
                .build();
        this.properties = properties.naverNews();
        this.resiliencePolicy = resiliencePolicy;
        this.requestBudget = requestBudget;
    }

    public List<NaverNewsArticle> search(String query, int display) {
        int effectiveDisplay = Math.max(1, Math.min(display, 100));
        String cacheKey = query.strip();
        CachedSearch cached = usableCached(cacheKey, effectiveDisplay);
        if (cached != null) {
            return limited(cached.articles(), effectiveDisplay);
        }
        Object queryLock = queryLocks[Math.floorMod(cacheKey.hashCode(), queryLocks.length)];
        synchronized (queryLock) {
            cached = usableCached(cacheKey, effectiveDisplay);
            if (cached != null) {
                return limited(cached.articles(), effectiveDisplay);
            }
            List<NaverNewsArticle> articles = request(cacheKey, effectiveDisplay);
            queryCache.put(
                    cacheKey,
                    new CachedSearch(
                            effectiveDisplay,
                            List.copyOf(articles),
                            Instant.now().plus(properties.queryCacheTtl())));
            trimCache();
            return articles;
        }
    }

    private List<NaverNewsArticle> request(String query, int display) {
        String clientId = properties.requiredClientId();
        String clientSecret = properties.requiredClientSecret();
        NaverNewsResponse response = resiliencePolicy.executeOnce("naver-news", () -> {
            // 실제 외부 요청 직전에 전역 일일 예산을 차감한다.
            requestBudget.consumeOrThrow();
            return restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v1/search/news.json")
                            .queryParam("query", query)
                            .queryParam("display", display)
                            .queryParam("sort", "date")
                            .build())
                    .header("X-Naver-Client-Id", clientId)
                    .header("X-Naver-Client-Secret", clientSecret)
                    .retrieve()
                    .body(NaverNewsResponse.class);
        });

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

    private CachedSearch usableCached(String cacheKey, int display) {
        CachedSearch cached = queryCache.get(cacheKey);
        if (cached == null || cached.display() < display || !Instant.now().isBefore(cached.expiresAt())) {
            if (cached != null && !Instant.now().isBefore(cached.expiresAt())) {
                queryCache.remove(cacheKey, cached);
            }
            return null;
        }
        return cached;
    }

    private List<NaverNewsArticle> limited(List<NaverNewsArticle> articles, int display) {
        return articles.size() <= display ? articles : List.copyOf(articles.subList(0, display));
    }

    private void trimCache() {
        int maxEntries = properties.queryCacheMaxEntries();
        if (queryCache.size() <= maxEntries) {
            return;
        }
        Instant now = Instant.now();
        queryCache.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().expiresAt()));
        int excess = queryCache.size() - maxEntries;
        if (excess <= 0) {
            return;
        }
        queryCache.entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getValue().expiresAt()))
                .limit(excess)
                .map(Map.Entry::getKey)
                .toList()
                .forEach(queryCache::remove);
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

    private record CachedSearch(
            int display,
            List<NaverNewsArticle> articles,
            Instant expiresAt) {
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

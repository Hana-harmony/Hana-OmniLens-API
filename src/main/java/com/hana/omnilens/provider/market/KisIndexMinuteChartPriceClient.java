package com.hana.omnilens.provider.market;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.StreamSupport;

import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.hana.omnilens.config.ExternalProviderProperties;
import com.hana.omnilens.provider.ExternalProviderResiliencePolicy;

@Component
public class KisIndexMinuteChartPriceClient {

    private static final DateTimeFormatter KIS_TIME = DateTimeFormatter.ofPattern("HHmmss");
    private static final String TIME_INDEX_CHART_PRICE_TR_ID = "FHKUP03500200";
    private static final int PAGE_SIZE_HINT = 100;
    private static final int MAX_PAGE_COUNT = 6;
    private static final Duration PAGE_REQUEST_INTERVAL = Duration.ofMillis(2_200);
    private static final int RATE_LIMIT_MAX_ATTEMPTS = 4;
    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");
    private static final LocalTime REGULAR_MARKET_OPEN = LocalTime.of(9, 0);
    private static final LocalTime REGULAR_MARKET_CLOSE = LocalTime.of(15, 30);

    private final RestClient restClient;
    private final ExternalProviderProperties.Kis kisProperties;
    private final KisAccessTokenProvider accessTokenProvider;
    private final ExternalProviderResiliencePolicy resiliencePolicy;

    public KisIndexMinuteChartPriceClient(
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

    public List<KisIndexMinuteChartPrice> findMinutePrices(String indexCode, LocalDate tradingDate, int limit) {
        if (!tradingDate.equals(LocalDate.now(KOREA_ZONE))) {
            return List.of();
        }
        int resolvedLimit = Math.max(1, Math.min(limit, PAGE_SIZE_HINT * MAX_PAGE_COUNT));
        String cursor = "";
        List<KisIndexMinuteChartPrice> prices = new ArrayList<>();
        Set<LocalDateTime> seenBuckets = new HashSet<>();

        for (int page = 0; page < MAX_PAGE_COUNT && prices.size() < resolvedLimit; page += 1) {
            List<KisIndexMinuteChartPrice> pagePrices = requestPageWithRateLimitRetry(indexCode, tradingDate, cursor)
                    .stream()
                    .filter(price -> price.bucketStart().toLocalDate().equals(tradingDate))
                    .filter(KisIndexMinuteChartPriceClient::isRegularSessionPrice)
                    .sorted(Comparator.comparing(KisIndexMinuteChartPrice::bucketStart).reversed())
                    .toList();
            if (pagePrices.isEmpty()) {
                break;
            }
            for (KisIndexMinuteChartPrice price : pagePrices) {
                if (seenBuckets.add(price.bucketStart())) {
                    prices.add(price);
                }
                if (prices.size() >= resolvedLimit) {
                    break;
                }
            }
            LocalTime earliestTime = pagePrices.get(pagePrices.size() - 1).bucketStart().toLocalTime();
            LocalTime nextCursor = earliestTime.minusSeconds(1);
            String nextCursorText = KIS_TIME.format(nextCursor);
            if (nextCursorText.equals(cursor) || earliestTime.equals(LocalTime.MIDNIGHT)) {
                break;
            }
            cursor = nextCursorText;
            waitForProviderQuota(1);
        }

        return prices.stream()
                .sorted(Comparator.comparing(KisIndexMinuteChartPrice::bucketStart))
                .toList();
    }

    private static boolean isRegularSessionPrice(KisIndexMinuteChartPrice price) {
        LocalTime time = price.bucketStart().toLocalTime();
        return !time.isBefore(REGULAR_MARKET_OPEN) && !time.isAfter(REGULAR_MARKET_CLOSE);
    }

    private List<KisIndexMinuteChartPrice> requestPageWithRateLimitRetry(
            String indexCode,
            LocalDate tradingDate,
            String cursor) {
        RestClientResponseException lastRateLimit = null;
        for (int attempt = 1; attempt <= RATE_LIMIT_MAX_ATTEMPTS; attempt += 1) {
            try {
                return requestPage(indexCode, tradingDate, cursor);
            } catch (RestClientResponseException exception) {
                if (!isKisRateLimit(exception)) {
                    throw exception;
                }
                lastRateLimit = exception;
                if (attempt < RATE_LIMIT_MAX_ATTEMPTS) {
                    waitForProviderQuota(attempt);
                }
            }
        }
        throw lastRateLimit;
    }

    private List<KisIndexMinuteChartPrice> requestPage(String indexCode, LocalDate tradingDate, String cursor) {
        String accessToken = accessTokenProvider.accessToken();
        String appKey = kisProperties.requiredAppKey();
        String appSecret = kisProperties.requiredAppSecret();
        JsonNode root = resiliencePolicy.execute("kis-time-index-chart-price", () -> restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/uapi/domestic-stock/v1/quotations/inquire-time-indexchartprice")
                        .queryParam("FID_COND_MRKT_DIV_CODE", "U")
                        .queryParam("FID_INPUT_ISCD", indexCode)
                        .queryParam("FID_INPUT_HOUR_1", cursor)
                        .queryParam("FID_PW_DATA_INCU_YN", "Y")
                        .queryParam("FID_ETC_CLS_CODE", "")
                        .build())
                .header("Content-Type", "application/json; charset=utf-8")
                .header("authorization", "Bearer " + accessToken)
                .header("appkey", appKey)
                .header("appsecret", appSecret)
                .header("tr_id", TIME_INDEX_CHART_PRICE_TR_ID)
                .retrieve()
                .body(JsonNode.class));

        JsonNode items = root == null ? null : root.path("output2");
        if (items == null || !items.isArray()) {
            return List.of();
        }
        return StreamSupport.stream(items.spliterator(), false)
                .map(item -> toMinutePrice(item, tradingDate))
                .toList();
    }

    private static boolean isKisRateLimit(RestClientResponseException exception) {
        String body = exception.getResponseBodyAsString();
        return body != null && body.contains("EGW00201");
    }

    private static void waitForProviderQuota(int attempt) {
        try {
            // KIS 지수 분봉도 주식 분봉과 같은 초당 제한을 받는다.
            Thread.sleep(PAGE_REQUEST_INTERVAL.multipliedBy(Math.max(1, attempt)).toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("KIS index minute chart request was interrupted", exception);
        }
    }

    private static KisIndexMinuteChartPrice toMinutePrice(JsonNode item, LocalDate fallbackDate) {
        LocalDate date = parseDate(item.path("stck_bsop_date").asText(), fallbackDate);
        LocalTime time = parseTime(item.path("stck_cntg_hour").asText());
        BigDecimal close = firstDecimal(item, "bstp_nmix_prpr", "stck_prpr");
        return new KisIndexMinuteChartPrice(
                LocalDateTime.of(date, time),
                firstDecimal(item, "bstp_nmix_oprc", "bstp_nmix_prpr"),
                firstDecimal(item, "bstp_nmix_hgpr", "bstp_nmix_prpr"),
                firstDecimal(item, "bstp_nmix_lwpr", "bstp_nmix_prpr"),
                close,
                firstLong(item, "cntg_vol", "acml_vol"),
                firstDecimal(item, "acml_tr_pbmn"));
    }

    private static LocalDate parseDate(String value, LocalDate fallbackDate) {
        String normalized = normalizeNumeric(value);
        if (normalized.length() != 8) {
            return fallbackDate;
        }
        return LocalDate.parse(normalized, DateTimeFormatter.BASIC_ISO_DATE);
    }

    private static LocalTime parseTime(String value) {
        String normalized = normalizeNumeric(value);
        if (normalized.length() < 6) {
            return LocalTime.MIDNIGHT;
        }
        try {
            return LocalTime.parse(normalized.substring(0, 6), KIS_TIME);
        } catch (DateTimeParseException exception) {
            return LocalTime.MIDNIGHT;
        }
    }

    private static long firstLong(JsonNode item, String... fieldNames) {
        for (String fieldName : fieldNames) {
            String normalized = normalizeNumeric(item.path(fieldName).asText());
            if (!normalized.isBlank()) {
                return Long.parseLong(normalized);
            }
        }
        return 0L;
    }

    private static BigDecimal firstDecimal(JsonNode item, String... fieldNames) {
        for (String fieldName : fieldNames) {
            String normalized = normalizeNumeric(item.path(fieldName).asText());
            if (!normalized.isBlank() && !"-".equals(normalized)) {
                return new BigDecimal(normalized);
            }
        }
        return BigDecimal.ZERO;
    }

    private static String normalizeNumeric(String value) {
        if (value == null) {
            return "";
        }
        return value.replace(",", "").replace("%", "").trim();
    }
}

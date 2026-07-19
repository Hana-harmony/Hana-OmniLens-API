package com.hana.omniconnect.provider.market;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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

import com.hana.omniconnect.config.ExternalProviderProperties;
import com.hana.omniconnect.provider.ExternalProviderResiliencePolicy;

@Component
public class KisMinuteChartPriceClient {

    private static final DateTimeFormatter KIS_TIME = DateTimeFormatter.ofPattern("HHmmss");
    private static final String TIME_ITEM_CHART_PRICE_TR_ID = "FHKST03010200";
    private static final String TIME_DAILY_CHART_PRICE_TR_ID = "FHKST03010230";
    private static final int PAGE_SIZE_HINT = 30;
    private static final int MAX_PAGE_COUNT = 20;
    private static final Duration PAGE_REQUEST_INTERVAL = Duration.ofMillis(2_200);
    private static final int RATE_LIMIT_MAX_ATTEMPTS = 4;
    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");
    private static final LocalTime REGULAR_MARKET_OPEN = LocalTime.of(9, 0);
    private static final LocalTime REGULAR_MARKET_CLOSE = LocalTime.of(15, 30);

    private final RestClient restClient;
    private final ExternalProviderProperties.Kis kisProperties;
    private final KisAccessTokenProvider accessTokenProvider;
    private final ExternalProviderResiliencePolicy resiliencePolicy;

    public KisMinuteChartPriceClient(
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

    public List<KisMinuteChartPrice> findMinutePrices(String stockCode, LocalDate tradingDate, int limit) {
        int resolvedLimit = Math.max(1, Math.min(limit, PAGE_SIZE_HINT * MAX_PAGE_COUNT));
        String cursor = KIS_TIME.format(REGULAR_MARKET_CLOSE);
        List<KisMinuteChartPrice> prices = new ArrayList<>();
        Set<LocalDateTime> seenBuckets = new HashSet<>();

        for (int page = 0; page < MAX_PAGE_COUNT && prices.size() < resolvedLimit; page += 1) {
            List<KisMinuteChartPrice> pagePrices = requestPageWithRateLimitRetry(stockCode, tradingDate, cursor)
                    .stream()
                    .filter(price -> price.bucketStart().toLocalDate().equals(tradingDate))
                    .filter(KisMinuteChartPriceClient::isRegularSessionPrice)
                    .sorted(Comparator.comparing(KisMinuteChartPrice::bucketStart).reversed())
                    .toList();
            if (pagePrices.isEmpty()) {
                break;
            }
            for (KisMinuteChartPrice price : pagePrices) {
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
                .sorted(Comparator.comparing(KisMinuteChartPrice::bucketStart))
                .toList();
    }

    private static boolean isRegularSessionPrice(KisMinuteChartPrice price) {
        LocalTime time = price.bucketStart().toLocalTime();
        return !time.isBefore(REGULAR_MARKET_OPEN) && !time.isAfter(REGULAR_MARKET_CLOSE);
    }

    private List<KisMinuteChartPrice> requestPageWithRateLimitRetry(
            String stockCode,
            LocalDate tradingDate,
            String cursor) {
        RestClientResponseException lastRateLimit = null;
        for (int attempt = 1; attempt <= RATE_LIMIT_MAX_ATTEMPTS; attempt += 1) {
            try {
                return requestPage(stockCode, tradingDate, cursor);
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

    private List<KisMinuteChartPrice> requestPage(String stockCode, LocalDate tradingDate, String cursor) {
        String accessToken = accessTokenProvider.accessToken();
        String appKey = kisProperties.requiredAppKey();
        String appSecret = kisProperties.requiredAppSecret();
        boolean today = tradingDate.equals(LocalDate.now(KOREA_ZONE));
        String path = today
                ? "/uapi/domestic-stock/v1/quotations/inquire-time-itemchartprice"
                : "/uapi/domestic-stock/v1/quotations/inquire-time-dailychartprice";
        String trId = today ? TIME_ITEM_CHART_PRICE_TR_ID : TIME_DAILY_CHART_PRICE_TR_ID;
        JsonNode root = resiliencePolicy.execute("kis-time-minute-chart-price", () -> restClient.get()
                .uri(uriBuilder -> {
                    var builder = uriBuilder
                            .path(path)
                            .queryParam("FID_COND_MRKT_DIV_CODE", "J")
                            .queryParam("FID_INPUT_ISCD", stockCode)
                            .queryParam("FID_INPUT_HOUR_1", cursor)
                            .queryParam("FID_PW_DATA_INCU_YN", "Y");
                    if (today) {
                        builder.queryParam("FID_ETC_CLS_CODE", "");
                    } else {
                        builder.queryParam("FID_INPUT_DATE_1", tradingDate.format(DateTimeFormatter.BASIC_ISO_DATE))
                                .queryParam("FID_FAKE_TICK_INCU_YN", "");
                    }
                    return builder.build();
                })
                .header("Content-Type", "application/json; charset=utf-8")
                .header("authorization", "Bearer " + accessToken)
                .header("appkey", appKey)
                .header("appsecret", appSecret)
                .header("tr_id", trId)
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
            // KIS 분봉 페이지 API는 짧은 연속 호출에서 초당 제한이 쉽게 발생한다.
            Thread.sleep(PAGE_REQUEST_INTERVAL.multipliedBy(Math.max(1, attempt)).toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("KIS minute chart request was interrupted", exception);
        }
    }

    private static KisMinuteChartPrice toMinutePrice(JsonNode item, LocalDate fallbackDate) {
        LocalDate date = parseDate(item.path("stck_bsop_date").asText(), fallbackDate);
        LocalTime time = parseTime(item.path("stck_cntg_hour").asText());
        BigDecimal close = firstDecimal(item, "stck_prpr", "stck_clpr");
        return new KisMinuteChartPrice(
                LocalDateTime.of(date, time),
                firstDecimal(item, "stck_oprc", "stck_prpr"),
                firstDecimal(item, "stck_hgpr", "stck_prpr"),
                firstDecimal(item, "stck_lwpr", "stck_prpr"),
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
        return LocalTime.parse(normalized.substring(0, 6), KIS_TIME);
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

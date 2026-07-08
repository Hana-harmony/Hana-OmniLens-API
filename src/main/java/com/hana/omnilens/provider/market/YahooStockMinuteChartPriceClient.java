package com.hana.omnilens.provider.market;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.hana.omnilens.market.domain.StockSummary;
import com.hana.omnilens.provider.ExternalProviderResiliencePolicy;

@Component
public class YahooStockMinuteChartPriceClient {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");
    private static final LocalTime REGULAR_MARKET_OPEN = LocalTime.of(9, 0);
    private static final LocalTime REGULAR_MARKET_CLOSE = LocalTime.of(15, 30);
    private static final int RECENT_INTRADAY_RANGE_DAYS = 8;

    private final RestClient restClient;
    private final ExternalProviderResiliencePolicy resiliencePolicy;
    private final Clock clock;

    @Autowired
    public YahooStockMinuteChartPriceClient(
            RestClient.Builder restClientBuilder,
            ExternalProviderResiliencePolicy resiliencePolicy) {
        this(restClientBuilder, resiliencePolicy, Clock.system(KOREA_ZONE));
    }

    YahooStockMinuteChartPriceClient(
            RestClient.Builder restClientBuilder,
            ExternalProviderResiliencePolicy resiliencePolicy,
            Clock clock) {
        this.restClient = restClientBuilder
                .baseUrl("https://query1.finance.yahoo.com")
                .build();
        this.resiliencePolicy = resiliencePolicy;
        this.clock = clock;
    }

    public List<KisMinuteChartPrice> findMinutePrices(StockSummary stock, LocalDate tradingDate, int limit) {
        String symbol = yahooSymbol(stock);
        LocalDate today = LocalDate.now(clock);
        if (symbol.isBlank()
                || tradingDate.isAfter(today)
                || tradingDate.isBefore(today.minusDays(RECENT_INTRADAY_RANGE_DAYS))) {
            return List.of();
        }
        JsonNode root = resiliencePolicy.execute("yahoo-stock-minute-chart-price", () -> restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v8/finance/chart/{symbol}")
                        .queryParam("interval", "1m")
                        .queryParam("range", RECENT_INTRADAY_RANGE_DAYS + "d")
                        .build(symbol))
                .header("User-Agent", "Mozilla/5.0")
                .retrieve()
                .body(JsonNode.class));
        int resolvedLimit = Math.max(1, limit);
        List<KisMinuteChartPrice> prices = parsePrices(root, tradingDate, resolvedLimit);
        if (!prices.isEmpty() || !tradingDate.equals(today)) {
            return prices;
        }
        return latestRegularSessionDate(root, today)
                .filter(latestTradingDate -> !latestTradingDate.equals(tradingDate))
                .map(latestTradingDate -> parsePrices(root, latestTradingDate, resolvedLimit))
                .orElse(prices);
    }

    private static List<KisMinuteChartPrice> parsePrices(JsonNode root, LocalDate tradingDate, int limit) {
        JsonNode result = root == null ? null : root.path("chart").path("result").path(0);
        JsonNode timestamps = result == null ? null : result.path("timestamp");
        JsonNode quote = result == null ? null : result.path("indicators").path("quote").path(0);
        if (timestamps == null || !timestamps.isArray() || quote == null || quote.isMissingNode()) {
            return List.of();
        }

        JsonNode opens = quote.path("open");
        JsonNode highs = quote.path("high");
        JsonNode lows = quote.path("low");
        JsonNode closes = quote.path("close");
        JsonNode volumes = quote.path("volume");
        List<KisMinuteChartPrice> prices = new ArrayList<>();
        for (int index = 0; index < timestamps.size() && prices.size() < limit; index += 1) {
            if (!hasNumber(closes, index)) {
                continue;
            }
            LocalDateTime bucketStart = LocalDateTime.ofInstant(
                    Instant.ofEpochSecond(timestamps.path(index).asLong()),
                    KOREA_ZONE);
            if (!bucketStart.toLocalDate().equals(tradingDate) || !isRegularSession(bucketStart.toLocalTime())) {
                continue;
            }
            BigDecimal close = decimalAt(closes, index);
            long volume = longAt(volumes, index);
            prices.add(new KisMinuteChartPrice(
                    bucketStart,
                    decimalAtOrDefault(opens, index, close),
                    decimalAtOrDefault(highs, index, close),
                    decimalAtOrDefault(lows, index, close),
                    close,
                    volume,
                    close.multiply(BigDecimal.valueOf(volume))));
        }
        return prices;
    }

    private static boolean isRegularSession(LocalTime time) {
        return !time.isBefore(REGULAR_MARKET_OPEN) && !time.isAfter(REGULAR_MARKET_CLOSE);
    }

    private static Optional<LocalDate> latestRegularSessionDate(JsonNode root, LocalDate cutoffDate) {
        JsonNode result = root == null ? null : root.path("chart").path("result").path(0);
        JsonNode timestamps = result == null ? null : result.path("timestamp");
        JsonNode quote = result == null ? null : result.path("indicators").path("quote").path(0);
        if (timestamps == null || !timestamps.isArray() || quote == null || quote.isMissingNode()) {
            return Optional.empty();
        }
        JsonNode closes = quote.path("close");
        LocalDate latestDate = null;
        for (int index = 0; index < timestamps.size(); index += 1) {
            if (!hasNumber(closes, index)) {
                continue;
            }
            LocalDateTime bucketStart = LocalDateTime.ofInstant(
                    Instant.ofEpochSecond(timestamps.path(index).asLong()),
                    KOREA_ZONE);
            if (bucketStart.toLocalDate().isAfter(cutoffDate) || !isRegularSession(bucketStart.toLocalTime())) {
                continue;
            }
            if (latestDate == null || bucketStart.toLocalDate().isAfter(latestDate)) {
                latestDate = bucketStart.toLocalDate();
            }
        }
        return Optional.ofNullable(latestDate);
    }

    private static String yahooSymbol(StockSummary stock) {
        String stockCode = stock.stockCode() == null ? "" : stock.stockCode().trim();
        if (!stockCode.matches("\\d{6}")) {
            return "";
        }
        return switch (stock.market()) {
            case "KOSPI" -> stockCode + ".KS";
            case "KOSDAQ" -> stockCode + ".KQ";
            default -> "";
        };
    }

    private static boolean hasNumber(JsonNode array, int index) {
        JsonNode node = array.path(index);
        return node.isNumber() || node.isTextual();
    }

    private static BigDecimal decimalAt(JsonNode array, int index) {
        return new BigDecimal(array.path(index).asText());
    }

    private static BigDecimal decimalAtOrDefault(JsonNode array, int index, BigDecimal fallback) {
        return hasNumber(array, index) ? decimalAt(array, index) : fallback;
    }

    private static long longAt(JsonNode array, int index) {
        JsonNode node = array.path(index);
        return node.isNumber() ? node.asLong() : 0L;
    }
}

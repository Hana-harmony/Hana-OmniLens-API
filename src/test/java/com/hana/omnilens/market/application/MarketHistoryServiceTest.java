package com.hana.omnilens.market.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.hana.omnilens.config.MarketHistoryCollectionProperties;
import com.hana.omnilens.market.domain.MarketDailyPrice;
import com.hana.omnilens.market.domain.MarketIntradayPrice;
import com.hana.omnilens.market.domain.StockSummary;
import com.hana.omnilens.provider.market.KisDailyChartPrice;
import com.hana.omnilens.provider.market.KisDailyChartPriceClient;
import com.hana.omnilens.provider.market.KisMinuteChartPrice;
import com.hana.omnilens.provider.market.KisMinuteChartPriceClient;
import com.hana.omnilens.provider.market.KrxOpenApiDailyTrade;
import com.hana.omnilens.provider.market.KrxOpenApiDailyTradeClient;

class MarketHistoryServiceTest {

    private final KrxOpenApiDailyTradeClient krxClient = mock(KrxOpenApiDailyTradeClient.class);
    private final KisDailyChartPriceClient kisDailyChartPriceClient = mock(KisDailyChartPriceClient.class);
    private final KisMinuteChartPriceClient kisMinuteChartPriceClient = mock(KisMinuteChartPriceClient.class);
    private final MarketDailyPriceRepository dailyPriceRepository = mock(MarketDailyPriceRepository.class);
    private final MarketIntradayPriceRepository intradayPriceRepository = mock(MarketIntradayPriceRepository.class);
    private final StockMasterRepository stockMasterRepository = mock(StockMasterRepository.class);
    private final MarketHistoryService service = new MarketHistoryService(
            krxClient,
            kisDailyChartPriceClient,
            dailyPriceRepository,
            stockMasterRepository,
            defaultCollectionProperties(),
            Clock.fixed(Instant.parse("2025-06-05T00:00:00Z"), ZoneId.of("Asia/Seoul")));

    @Test
    void collectDailyHistorySavesKrxRowsForSupportedStocks() {
        LocalDate baseDate = LocalDate.of(2025, 6, 4);
        when(krxClient.findDailyTrades("KOSPI", baseDate)).thenReturn(List.of(
                new KrxOpenApiDailyTrade(
                        baseDate,
                        "KR7005930003",
                        "005930",
                        "삼성전자",
                        "KOSPI",
                        new BigDecimal("57900"),
                        new BigDecimal("58900"),
                        new BigDecimal("57500"),
                        new BigDecimal("58700"),
                        new BigDecimal("1.91"),
                        19_123_456L,
                        new BigDecimal("1122334455000")),
                new KrxOpenApiDailyTrade(
                        baseDate,
                        "KR7999999999",
                        "999999",
                        "미지원종목",
                        "KONEX",
                        BigDecimal.ONE,
                        BigDecimal.ONE,
                        BigDecimal.ONE,
                        BigDecimal.ONE,
                        BigDecimal.ZERO,
                        1L,
                        BigDecimal.ONE)));
        when(krxClient.findDailyTrades("KOSDAQ", baseDate)).thenReturn(List.of());
        when(krxClient.findDailyTrades("KONEX", baseDate)).thenReturn(List.of());
        when(stockMasterRepository.findByCode("005930")).thenReturn(Optional.of(new StockSummary(
                "005930",
                "삼성전자",
                "Samsung Electronics",
                "KOSPI",
                "KR7005930003",
                "00126380")));
        when(stockMasterRepository.findByCode("999999")).thenReturn(Optional.empty());
        when(dailyPriceRepository.upsertAll(anyList())).thenReturn(1);

        MarketHistoryCollectionResult result = service.collectDailyHistory(baseDate);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MarketDailyPrice>> captor = ArgumentCaptor.forClass(List.class);
        verify(dailyPriceRepository).upsertAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        MarketDailyPrice saved = captor.getValue().get(0);
        assertThat(saved.stockCode()).isEqualTo("005930");
        assertThat(saved.openPriceKrw()).isEqualByComparingTo("57900");
        assertThat(saved.highPriceKrw()).isEqualByComparingTo("58900");
        assertThat(saved.lowPriceKrw()).isEqualByComparingTo("57500");
        assertThat(saved.closePriceKrw()).isEqualByComparingTo("58700");
        assertThat(saved.tradingValueKrw()).isEqualByComparingTo("1122334455000");
        assertThat(saved.source()).isEqualTo("KRX_OPEN_API_DAILY_TRADE");
        assertThat(result.requestedCount()).isEqualTo(2);
        assertThat(result.savedCount()).isEqualTo(1);
        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.marketResults()).hasSize(3);
        assertThat(result.marketResults().get(0).market()).isEqualTo("KOSPI");
        assertThat(result.marketResults().get(0).status()).isEqualTo("SUCCESS");
    }

    @Test
    void collectDailyHistoryReturnsFailedMarketResultWhenKrxProviderFails() {
        LocalDate baseDate = LocalDate.of(2025, 6, 4);
        when(krxClient.findDailyTrades("KOSPI", baseDate))
                .thenThrow(new IllegalStateException("KRX provider unavailable"));
        when(krxClient.findDailyTrades("KOSDAQ", baseDate)).thenReturn(List.of());
        when(krxClient.findDailyTrades("KONEX", baseDate)).thenReturn(List.of());

        MarketHistoryCollectionResult result = service.collectDailyHistory(baseDate);

        assertThat(result.requestedCount()).isZero();
        assertThat(result.savedCount()).isZero();
        assertThat(result.status()).isEqualTo("PARTIAL_FAILED");
        assertThat(result.marketResults()).extracting(MarketHistoryCollectionResult.MarketResult::market)
                .containsExactly("KOSPI", "KOSDAQ", "KONEX", "KIS_DAILY_CHART");
        assertThat(result.marketResults().get(0).status()).isEqualTo("FAILED");
        assertThat(result.marketResults().get(0).errorMessage()).contains("KRX provider unavailable");
    }

    @Test
    void collectDailyHistoryFallsBackToKisDailyChartWhenKrxProviderFails() {
        LocalDate baseDate = LocalDate.of(2025, 6, 4);
        StockSummary stock = new StockSummary(
                "005930",
                "삼성전자",
                "Samsung Electronics",
                "KOSPI",
                "KR7005930003",
                "00126380");
        when(krxClient.findDailyTrades("KOSPI", baseDate))
                .thenThrow(new IllegalStateException("KRX provider unavailable"));
        when(krxClient.findDailyTrades("KOSDAQ", baseDate))
                .thenThrow(new IllegalStateException("KRX provider unavailable"));
        when(krxClient.findDailyTrades("KONEX", baseDate))
                .thenThrow(new IllegalStateException("KRX provider unavailable"));
        when(stockMasterRepository.findAll(2_000)).thenReturn(List.of(stock));
        when(kisDailyChartPriceClient.findDailyPrices("005930", baseDate, baseDate)).thenReturn(List.of(
                new KisDailyChartPrice(
                        baseDate,
                        new BigDecimal("57900"),
                        new BigDecimal("58900"),
                        new BigDecimal("57500"),
                        new BigDecimal("58700"),
                        new BigDecimal("1.91"),
                        19_123_456L,
                        new BigDecimal("1122334455000"))));
        when(dailyPriceRepository.upsertAll(anyList())).thenReturn(1);

        MarketHistoryCollectionResult result = service.collectDailyHistory(baseDate);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MarketDailyPrice>> captor = ArgumentCaptor.forClass(List.class);
        verify(dailyPriceRepository).upsertAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().get(0).stockCode()).isEqualTo("005930");
        assertThat(captor.getValue().get(0).source()).isEqualTo("KIS_DAILY_ITEM_CHART_PRICE");
        assertThat(result.requestedCount()).isEqualTo(1);
        assertThat(result.savedCount()).isEqualTo(1);
        assertThat(result.source()).isEqualTo("KRX_OPEN_API_DAILY_TRADE+KIS_DAILY_ITEM_CHART_PRICE");
        assertThat(result.status()).isEqualTo("PARTIAL_FAILED");
        assertThat(result.marketResults()).extracting(MarketHistoryCollectionResult.MarketResult::market)
                .containsExactly("KOSPI", "KOSDAQ", "KONEX", "KIS_DAILY_CHART");
        assertThat(result.marketResults().get(3).status()).isEqualTo("SUCCESS");
    }

    @Test
    void collectDailyHistoryUsesKisDailyChartAsPrimaryProviderWhenConfigured() {
        LocalDate baseDate = LocalDate.of(2025, 6, 4);
        StockSummary stock = new StockSummary(
                "005930",
                "삼성전자",
                "Samsung Electronics",
                "KOSPI",
                "KR7005930003",
                "00126380");
        MarketHistoryService kisPrimaryService = new MarketHistoryService(
                krxClient,
                kisDailyChartPriceClient,
                dailyPriceRepository,
                stockMasterRepository,
                new MarketHistoryCollectionProperties(
                        true,
                        86_400_000L,
                        1,
                        MarketHistoryCollectionProperties.Provider.KIS_DAILY_CHART),
                Clock.fixed(Instant.parse("2025-06-05T00:00:00Z"), ZoneId.of("Asia/Seoul")));
        when(stockMasterRepository.findAll(2_000)).thenReturn(List.of(stock));
        when(kisDailyChartPriceClient.findDailyPrices("005930", baseDate, baseDate)).thenReturn(List.of(
                new KisDailyChartPrice(
                        baseDate,
                        new BigDecimal("57900"),
                        new BigDecimal("58900"),
                        new BigDecimal("57500"),
                        new BigDecimal("58700"),
                        new BigDecimal("1.91"),
                        19_123_456L,
                        new BigDecimal("1122334455000"))));
        when(dailyPriceRepository.upsertAll(anyList())).thenReturn(1);

        MarketHistoryCollectionResult result = kisPrimaryService.collectDailyHistory(baseDate);

        assertThat(result.source()).isEqualTo("KIS_DAILY_ITEM_CHART_PRICE");
        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.marketResults()).extracting(MarketHistoryCollectionResult.MarketResult::market)
                .containsExactly("KIS_DAILY_CHART");
        assertThat(result.requestedCount()).isEqualTo(1);
        assertThat(result.savedCount()).isEqualTo(1);
    }

    @Test
    void getHistoryFallsBackToKisDailyChartWhenKrxRepositoryIsEmpty() {
        LocalDate from = LocalDate.of(2025, 6, 1);
        LocalDate to = LocalDate.of(2025, 6, 4);
        StockSummary stock = new StockSummary(
                "005930",
                "삼성전자",
                "Samsung Electronics",
                "KOSPI",
                "KR7005930003",
                "00126380");
        when(stockMasterRepository.findByCode("005930")).thenReturn(Optional.of(stock));
        when(dailyPriceRepository.findByStockCode("005930", from, to, 10)).thenReturn(List.of());
        when(kisDailyChartPriceClient.findDailyPrices("005930", from, to)).thenReturn(List.of(
                new KisDailyChartPrice(
                        LocalDate.of(2025, 6, 4),
                        new BigDecimal("57900"),
                        new BigDecimal("58900"),
                        new BigDecimal("57500"),
                        new BigDecimal("58700"),
                        new BigDecimal("1.91"),
                        19_123_456L,
                        new BigDecimal("1122334455000"))));
        when(dailyPriceRepository.upsertAll(anyList())).thenReturn(1);

        List<MarketDailyPrice> prices = service.getHistory("005930", from, to, 10);

        assertThat(prices).hasSize(1);
        assertThat(prices.get(0).source()).isEqualTo("KIS_DAILY_ITEM_CHART_PRICE");
        verify(dailyPriceRepository).upsertAll(anyList());
    }

    @Test
    void getHistoryBackfillsKisDailyChartWhenSavedRowsDoNotCoverRequestedStart() {
        LocalDate from = LocalDate.of(2025, 6, 1);
        LocalDate to = LocalDate.of(2025, 7, 1);
        StockSummary stock = stock();
        MarketDailyPrice savedRecent = dailyPrice(LocalDate.of(2025, 7, 1), "KRX_OPEN_API_DAILY_TRADE");
        when(stockMasterRepository.findByCode("005930")).thenReturn(Optional.of(stock));
        when(dailyPriceRepository.findByStockCode("005930", from, to, 365)).thenReturn(List.of(savedRecent));
        when(kisDailyChartPriceClient.findDailyPrices("005930", from, to)).thenReturn(List.of(
                new KisDailyChartPrice(
                        LocalDate.of(2025, 6, 2),
                        new BigDecimal("57000"),
                        new BigDecimal("58000"),
                        new BigDecimal("56000"),
                        new BigDecimal("57500"),
                        new BigDecimal("0.10"),
                        10_000L,
                        new BigDecimal("575000000")),
                new KisDailyChartPrice(
                        LocalDate.of(2025, 7, 1),
                        new BigDecimal("59000"),
                        new BigDecimal("60000"),
                        new BigDecimal("58000"),
                        new BigDecimal("59500"),
                        new BigDecimal("0.20"),
                        20_000L,
                        new BigDecimal("1190000000"))));
        when(dailyPriceRepository.upsertAll(anyList())).thenReturn(2);

        List<MarketDailyPrice> prices = service.getHistory("005930", from, to, 365);

        assertThat(prices).extracting(MarketDailyPrice::tradeDate)
                .containsExactly(LocalDate.of(2025, 6, 2), LocalDate.of(2025, 7, 1));
        assertThat(prices.get(0).source()).isEqualTo("KIS_DAILY_ITEM_CHART_PRICE");
        assertThat(prices.get(1).source()).isEqualTo("KRX_OPEN_API_DAILY_TRADE");
        verify(dailyPriceRepository).upsertAll(anyList());
    }

    @Test
    void getIntradayHistoryUsesFreshSavedMinutePricesWithoutKisRequest() {
        Clock fixedClock = Clock.fixed(Instant.parse("2025-06-05T01:00:30Z"), ZoneId.of("Asia/Seoul"));
        MarketHistoryService intradayService = intradayService(fixedClock);
        LocalDate today = LocalDate.of(2025, 6, 5);
        StockSummary stock = stock();
        MarketIntradayPrice saved = intradayPrice(today.atTime(9, 1), Instant.parse("2025-06-05T01:00:00Z"));
        when(stockMasterRepository.findByCode("005930")).thenReturn(Optional.of(stock));
        when(intradayPriceRepository.findByStockCodeAndDate("005930", today, 390)).thenReturn(List.of(saved));

        List<MarketIntradayPrice> prices = intradayService.getIntradayHistory("005930", today, 390);

        assertThat(prices).containsExactly(saved);
        verifyNoInteractions(kisMinuteChartPriceClient);
    }

    @Test
    void getIntradayHistoryUsesSavedRegularSessionMinutesAfterMarketCloseWithoutKisRequest() {
        Clock fixedClock = Clock.fixed(Instant.parse("2025-06-05T07:00:30Z"), ZoneId.of("Asia/Seoul"));
        MarketHistoryService intradayService = intradayService(fixedClock);
        LocalDate today = LocalDate.of(2025, 6, 5);
        StockSummary stock = stock();
        List<MarketIntradayPrice> savedRegularSession = completeRegularSessionPrices(
                today,
                Instant.parse("2025-06-05T06:00:00Z"));
        when(stockMasterRepository.findByCode("005930")).thenReturn(Optional.of(stock));
        when(intradayPriceRepository.findByStockCodeAndDate("005930", today, 390))
                .thenReturn(savedRegularSession);

        List<MarketIntradayPrice> prices = intradayService.getIntradayHistory("005930", today, 390);

        assertThat(prices).hasSize(390);
        verifyNoInteractions(kisMinuteChartPriceClient);
    }

    @Test
    void getIntradayHistoryRefetchesPartialRegularSessionMinutesAfterMarketClose() {
        Clock fixedClock = Clock.fixed(Instant.parse("2025-06-05T07:00:30Z"), ZoneId.of("Asia/Seoul"));
        MarketHistoryService intradayService = intradayService(fixedClock);
        LocalDate today = LocalDate.of(2025, 6, 5);
        StockSummary stock = stock();
        MarketIntradayPrice staleOpen = intradayPrice(today.atTime(9, 1), Instant.parse("2025-06-05T06:00:00Z"));
        MarketIntradayPrice staleClose = intradayPrice(today.atTime(15, 30), Instant.parse("2025-06-05T06:00:00Z"));
        MarketIntradayPrice savedAfterUpsert = intradayPrice(today.atTime(9, 2), Instant.parse("2025-06-05T07:00:30Z"));
        when(stockMasterRepository.findByCode("005930")).thenReturn(Optional.of(stock));
        when(intradayPriceRepository.findByStockCodeAndDate("005930", today, 390))
                .thenReturn(List.of(staleOpen, staleClose))
                .thenReturn(List.of(staleOpen, savedAfterUpsert, staleClose));
        when(kisMinuteChartPriceClient.findMinutePrices("005930", today, 390)).thenReturn(List.of(
                new KisMinuteChartPrice(
                        today.atTime(9, 2),
                        new BigDecimal("58000"),
                        new BigDecimal("58100"),
                        new BigDecimal("57900"),
                        new BigDecimal("58050"),
                        12_345L,
                        new BigDecimal("716000000"))));

        List<MarketIntradayPrice> prices = intradayService.getIntradayHistory("005930", today, 390);

        assertThat(prices).extracting(MarketIntradayPrice::bucketStart)
                .containsExactly(today.atTime(9, 1), today.atTime(9, 2), today.atTime(15, 30));
        verify(intradayPriceRepository).upsertAll(anyList());
    }

    @Test
    void getIntradayHistoryStoresKisMinutePricesWhenSavedCacheIsMissing() {
        Clock fixedClock = Clock.fixed(Instant.parse("2025-06-05T01:00:30Z"), ZoneId.of("Asia/Seoul"));
        MarketHistoryService intradayService = intradayService(fixedClock);
        LocalDate today = LocalDate.of(2025, 6, 5);
        StockSummary stock = stock();
        MarketIntradayPrice savedAfterUpsert = intradayPrice(today.atTime(9, 1), Instant.parse("2025-06-05T01:00:30Z"));
        when(stockMasterRepository.findByCode("005930")).thenReturn(Optional.of(stock));
        when(intradayPriceRepository.findByStockCodeAndDate("005930", today, 390))
                .thenReturn(List.of())
                .thenReturn(List.of(savedAfterUpsert));
        when(kisMinuteChartPriceClient.findMinutePrices("005930", today, 390)).thenReturn(List.of(
                new KisMinuteChartPrice(
                        today.atTime(9, 1),
                        new BigDecimal("58000"),
                        new BigDecimal("58100"),
                        new BigDecimal("57900"),
                        new BigDecimal("58050"),
                        12_345L,
                        new BigDecimal("716000000"))));

        List<MarketIntradayPrice> prices = intradayService.getIntradayHistory("005930", today, 390);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MarketIntradayPrice>> captor = ArgumentCaptor.forClass(List.class);
        verify(intradayPriceRepository).upsertAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().get(0).stockCode()).isEqualTo("005930");
        assertThat(captor.getValue().get(0).source()).isEqualTo("KIS_TIME_ITEM_CHART_PRICE");
        assertThat(prices).containsExactly(savedAfterUpsert);
    }

    @Test
    void getIntradayHistoryStoresHistoricalMinutePricesWithDailyChartSource() {
        Clock fixedClock = Clock.fixed(Instant.parse("2025-06-05T01:00:30Z"), ZoneId.of("Asia/Seoul"));
        MarketHistoryService intradayService = intradayService(fixedClock);
        LocalDate tradeDate = LocalDate.of(2025, 6, 4);
        StockSummary stock = stock();
        MarketIntradayPrice savedAfterUpsert = new MarketIntradayPrice(
                "005930",
                tradeDate.atTime(9, 1),
                "KOSPI",
                new BigDecimal("58000"),
                new BigDecimal("58100"),
                new BigDecimal("57900"),
                new BigDecimal("58050"),
                12_345L,
                new BigDecimal("716000000"),
                "KIS_TIME_DAILY_CHART_PRICE",
                Instant.parse("2025-06-05T01:00:30Z"));
        when(stockMasterRepository.findByCode("005930")).thenReturn(Optional.of(stock));
        when(intradayPriceRepository.findByStockCodeAndDate("005930", tradeDate, 390))
                .thenReturn(List.of())
                .thenReturn(List.of(savedAfterUpsert));
        when(kisMinuteChartPriceClient.findMinutePrices("005930", tradeDate, 390)).thenReturn(List.of(
                new KisMinuteChartPrice(
                        tradeDate.atTime(9, 1),
                        new BigDecimal("58000"),
                        new BigDecimal("58100"),
                        new BigDecimal("57900"),
                        new BigDecimal("58050"),
                        12_345L,
                        new BigDecimal("716000000"))));

        List<MarketIntradayPrice> prices = intradayService.getIntradayHistory("005930", tradeDate, 390);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MarketIntradayPrice>> captor = ArgumentCaptor.forClass(List.class);
        verify(intradayPriceRepository).upsertAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().get(0).source()).isEqualTo("KIS_TIME_DAILY_CHART_PRICE");
        assertThat(prices).containsExactly(savedAfterUpsert);
    }

    @Test
    void getIntradayHistoryIgnoresAfterHoursCacheAndFetchesRegularSessionMinutes() {
        Clock fixedClock = Clock.fixed(Instant.parse("2025-06-05T08:00:30Z"), ZoneId.of("Asia/Seoul"));
        MarketHistoryService intradayService = intradayService(fixedClock);
        LocalDate today = LocalDate.of(2025, 6, 5);
        StockSummary stock = stock();
        MarketIntradayPrice staleAfterHours = intradayPrice(today.atTime(17, 30), Instant.parse("2025-06-05T08:00:00Z"));
        MarketIntradayPrice savedAfterUpsert = intradayPrice(today.atTime(9, 1), Instant.parse("2025-06-05T08:00:30Z"));
        when(stockMasterRepository.findByCode("005930")).thenReturn(Optional.of(stock));
        when(intradayPriceRepository.findByStockCodeAndDate("005930", today, 390))
                .thenReturn(List.of(staleAfterHours))
                .thenReturn(List.of(staleAfterHours, savedAfterUpsert));
        when(kisMinuteChartPriceClient.findMinutePrices("005930", today, 390)).thenReturn(List.of(
                new KisMinuteChartPrice(
                        today.atTime(9, 1),
                        new BigDecimal("58000"),
                        new BigDecimal("58100"),
                        new BigDecimal("57900"),
                        new BigDecimal("58050"),
                        12_345L,
                        new BigDecimal("716000000"))));

        List<MarketIntradayPrice> prices = intradayService.getIntradayHistory("005930", today, 390);

        assertThat(prices).extracting(MarketIntradayPrice::bucketStart)
                .containsExactly(today.atTime(9, 1));
        verify(intradayPriceRepository).upsertAll(anyList());
    }

    private MarketHistoryService intradayService(Clock clock) {
        return new MarketHistoryService(
                krxClient,
                kisDailyChartPriceClient,
                kisMinuteChartPriceClient,
                dailyPriceRepository,
                intradayPriceRepository,
                stockMasterRepository,
                defaultCollectionProperties(),
                null,
                clock);
    }

    private static StockSummary stock() {
        return new StockSummary(
                "005930",
                "삼성전자",
                "Samsung Electronics",
                "KOSPI",
                "KR7005930003",
                "00126380");
    }

    private static MarketIntradayPrice intradayPrice(LocalDateTime bucketStart, Instant collectedAt) {
        return new MarketIntradayPrice(
                "005930",
                bucketStart,
                "KOSPI",
                new BigDecimal("58000"),
                new BigDecimal("58100"),
                new BigDecimal("57900"),
                new BigDecimal("58050"),
                12_345L,
                new BigDecimal("716000000"),
                "KIS_TIME_ITEM_CHART_PRICE",
                collectedAt);
    }

    private static List<MarketIntradayPrice> completeRegularSessionPrices(LocalDate tradeDate, Instant collectedAt) {
        List<MarketIntradayPrice> prices = new ArrayList<>();
        for (int minuteOffset = 0; minuteOffset < 390; minuteOffset += 1) {
            prices.add(intradayPrice(tradeDate.atTime(9, 1).plusMinutes(minuteOffset), collectedAt));
        }
        return prices;
    }

    private static MarketDailyPrice dailyPrice(LocalDate tradeDate, String source) {
        return new MarketDailyPrice(
                "005930",
                tradeDate,
                "KOSPI",
                new BigDecimal("59000"),
                new BigDecimal("60000"),
                new BigDecimal("58000"),
                new BigDecimal("59500"),
                BigDecimal.ZERO,
                20_000L,
                new BigDecimal("1190000000"),
                new BigDecimal("59500"),
                source,
                Instant.parse("2025-07-01T07:00:00Z"));
    }

    private static MarketHistoryCollectionProperties defaultCollectionProperties() {
        return new MarketHistoryCollectionProperties(
                true,
                86_400_000L,
                1,
                MarketHistoryCollectionProperties.Provider.KRX_OPEN_API_WITH_KIS_BACKUP);
    }
}

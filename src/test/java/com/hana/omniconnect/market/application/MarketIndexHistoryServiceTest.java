package com.hana.omniconnect.market.application;

import static org.assertj.core.api.Assertions.assertThat;
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
import java.util.List;

import org.junit.jupiter.api.Test;

import com.hana.omniconnect.market.domain.MarketIndexIntradayPrice;
import com.hana.omniconnect.provider.market.KisIndexMinuteChartPrice;
import com.hana.omniconnect.provider.market.KisIndexMinuteChartPriceClient;
import com.hana.omniconnect.provider.market.YahooIndexMinuteChartPriceClient;

class MarketIndexHistoryServiceTest {

    private static final Clock PRE_OPEN_CLOCK = Clock.fixed(
            Instant.parse("2026-07-02T23:30:00Z"),
            ZoneId.of("Asia/Seoul"));

    @Test
    void getIntradayHistoryUsesLatestStoredTradingDateBeforeMarketOpen() {
        KisIndexMinuteChartPriceClient kisClient = mock(KisIndexMinuteChartPriceClient.class);
        InMemoryMarketIndexSnapshotRepository repository = new InMemoryMarketIndexSnapshotRepository();
        repository.recordRealtimeMinute(intraday(LocalDateTime.of(2026, 7, 2, 15, 30), "2890.12"));
        MarketIndexHistoryService service = new MarketIndexHistoryService(kisClient, repository, PRE_OPEN_CLOCK);

        List<MarketIndexIntradayPrice> prices = service.getIntradayHistory("0001", null, 390);

        verifyNoInteractions(kisClient);
        assertThat(prices).hasSize(1);
        assertThat(prices.get(0).bucketStart().toLocalDate()).isEqualTo(LocalDate.of(2026, 7, 2));
        assertThat(prices.get(0).closeValue()).isEqualByComparingTo("2890.12");
    }

    @Test
    void getIntradayHistoryFetchesAndStoresWhenSavedDataIsMissing() {
        KisIndexMinuteChartPriceClient kisClient = mock(KisIndexMinuteChartPriceClient.class);
        InMemoryMarketIndexSnapshotRepository repository = new InMemoryMarketIndexSnapshotRepository();
        MarketIndexHistoryService service = new MarketIndexHistoryService(kisClient, repository, PRE_OPEN_CLOCK);
        LocalDate explicitDate = LocalDate.of(2026, 7, 2);
        when(kisClient.findMinutePrices("0001", explicitDate, 390)).thenReturn(List.of(kisPrice(
                LocalDateTime.of(2026, 7, 2, 9, 1),
                "2881.00")));

        List<MarketIndexIntradayPrice> prices = service.getIntradayHistory("0001", explicitDate, 390);

        verify(kisClient).findMinutePrices("0001", explicitDate, 390);
        assertThat(prices).hasSize(1);
        assertThat(repository.findIntraday("0001", explicitDate, 390)).hasSize(1);
    }

    @Test
    void getIntradayHistoryIgnoresStoredAfterHoursIndexTicks() {
        KisIndexMinuteChartPriceClient kisClient = mock(KisIndexMinuteChartPriceClient.class);
        InMemoryMarketIndexSnapshotRepository repository = new InMemoryMarketIndexSnapshotRepository();
        LocalDate explicitDate = LocalDate.of(2026, 7, 2);
        repository.recordRealtimeMinute(intraday(LocalDateTime.of(2026, 7, 2, 18, 5), "8088.34"));
        when(kisClient.findMinutePrices("0001", explicitDate, 390)).thenReturn(List.of(kisPrice(
                LocalDateTime.of(2026, 7, 2, 10, 46),
                "2891.00")));
        MarketIndexHistoryService service = new MarketIndexHistoryService(kisClient, repository, PRE_OPEN_CLOCK);

        List<MarketIndexIntradayPrice> prices = service.getIntradayHistory("0001", explicitDate, 390);

        verify(kisClient).findMinutePrices("0001", explicitDate, 390);
        assertThat(prices).hasSize(1);
        assertThat(prices.get(0).bucketStart().toLocalTime().toString()).isEqualTo("10:46");
        assertThat(prices.get(0).closeValue()).isEqualByComparingTo("2891.00");
    }

    @Test
    void getIntradayHistoryUsesStoredRegularSessionRealtimeIndexSource() {
        KisIndexMinuteChartPriceClient kisClient = mock(KisIndexMinuteChartPriceClient.class);
        InMemoryMarketIndexSnapshotRepository repository = new InMemoryMarketIndexSnapshotRepository();
        LocalDate explicitDate = LocalDate.of(2026, 7, 2);
        repository.recordRealtimeMinute(intraday(
                LocalDateTime.of(2026, 7, 2, 10, 46),
                "8088.34",
                "KIS_REALTIME_INDEX"));
        MarketIndexHistoryService service = new MarketIndexHistoryService(kisClient, repository, PRE_OPEN_CLOCK);

        List<MarketIndexIntradayPrice> prices = service.getIntradayHistory("0001", explicitDate, 390);

        verifyNoInteractions(kisClient);
        assertThat(prices).hasSize(1);
        assertThat(prices.get(0).closeValue()).isEqualByComparingTo("8088.34");
    }

    @Test
    void getIntradayHistoryKeepsLatestStoredBucketsWhenLimitIsSmallerThanSession() {
        KisIndexMinuteChartPriceClient kisClient = mock(KisIndexMinuteChartPriceClient.class);
        InMemoryMarketIndexSnapshotRepository repository = new InMemoryMarketIndexSnapshotRepository();
        LocalDate explicitDate = LocalDate.of(2026, 7, 2);
        repository.recordRealtimeMinute(intraday(LocalDateTime.of(2026, 7, 2, 9, 0), "2880.00"));
        repository.recordRealtimeMinute(intraday(LocalDateTime.of(2026, 7, 2, 9, 1), "2881.00"));
        repository.recordRealtimeMinute(intraday(LocalDateTime.of(2026, 7, 2, 15, 30), "2899.00"));
        MarketIndexHistoryService service = new MarketIndexHistoryService(kisClient, repository, PRE_OPEN_CLOCK);

        List<MarketIndexIntradayPrice> prices = service.getIntradayHistory("0001", explicitDate, 2);

        verifyNoInteractions(kisClient);
        assertThat(prices)
                .extracting(price -> price.bucketStart().toLocalTime().toString())
                .containsExactly("09:01", "15:30");
    }

    @Test
    void getIntradayHistoryBackfillsPartialTodaySeries() {
        KisIndexMinuteChartPriceClient kisClient = mock(KisIndexMinuteChartPriceClient.class);
        InMemoryMarketIndexSnapshotRepository repository = new InMemoryMarketIndexSnapshotRepository();
        Clock duringMarketClock = Clock.fixed(
                Instant.parse("2026-07-02T05:00:00Z"),
                ZoneId.of("Asia/Seoul"));
        LocalDate explicitDate = LocalDate.of(2026, 7, 2);
        repository.recordRealtimeMinute(intraday(LocalDateTime.of(2026, 7, 2, 13, 59), "2888.00"));
        when(kisClient.findMinutePrices("0001", explicitDate, 390)).thenReturn(List.of(
                kisPrice(LocalDateTime.of(2026, 7, 2, 9, 1), "2881.00"),
                kisPrice(LocalDateTime.of(2026, 7, 2, 14, 0), "2892.00")));
        MarketIndexHistoryService service = new MarketIndexHistoryService(kisClient, repository, duringMarketClock);

        List<MarketIndexIntradayPrice> prices = service.getIntradayHistory("0001", explicitDate, 390);

        verify(kisClient).findMinutePrices("0001", explicitDate, 390);
        assertThat(prices)
                .extracting(price -> price.bucketStart().toLocalTime().toString())
                .containsExactly("09:01", "13:59", "14:00");
        assertThat(prices)
                .extracting(MarketIndexIntradayPrice::closeValue)
                .containsExactly(new BigDecimal("2881.00"), new BigDecimal("2888.00"), new BigDecimal("2892.00"));
    }

    @Test
    void getIntradayHistoryUsesYahooFallbackWhenKisSeriesIsStillTooShort() {
        KisIndexMinuteChartPriceClient kisClient = mock(KisIndexMinuteChartPriceClient.class);
        YahooIndexMinuteChartPriceClient yahooClient = mock(YahooIndexMinuteChartPriceClient.class);
        InMemoryMarketIndexSnapshotRepository repository = new InMemoryMarketIndexSnapshotRepository();
        Clock afterMarketClock = Clock.fixed(
                Instant.parse("2026-07-02T07:00:00Z"),
                ZoneId.of("Asia/Seoul"));
        LocalDate explicitDate = LocalDate.of(2026, 7, 2);
        when(kisClient.findMinutePrices("0001", explicitDate, 390)).thenReturn(List.of());
        when(yahooClient.findMinutePrices("0001", explicitDate, 390)).thenReturn(List.of(
                kisPrice(LocalDateTime.of(2026, 7, 2, 9, 0), "2880.00"),
                kisPrice(LocalDateTime.of(2026, 7, 2, 9, 1), "2881.00"),
                kisPrice(LocalDateTime.of(2026, 7, 2, 15, 30), "2899.00")));
        MarketIndexHistoryService service =
                new MarketIndexHistoryService(kisClient, yahooClient, repository, afterMarketClock);

        List<MarketIndexIntradayPrice> prices = service.getIntradayHistory("0001", explicitDate, 390);

        verify(kisClient).findMinutePrices("0001", explicitDate, 390);
        verify(yahooClient).findMinutePrices("0001", explicitDate, 390);
        assertThat(prices)
                .extracting(price -> price.bucketStart().toLocalTime().toString())
                .containsExactly("09:00", "09:01", "15:30");
        assertThat(prices)
                .extracting(MarketIndexIntradayPrice::source)
                .containsOnly("YAHOO_FINANCE_INDEX_CHART");
    }

    @Test
    void getPreviousCloseUsesStoredPreviousSessionWithoutProviderCall() {
        KisIndexMinuteChartPriceClient kisClient = mock(KisIndexMinuteChartPriceClient.class);
        YahooIndexMinuteChartPriceClient yahooClient = mock(YahooIndexMinuteChartPriceClient.class);
        InMemoryMarketIndexSnapshotRepository repository = new InMemoryMarketIndexSnapshotRepository();
        repository.recordRealtimeMinute(intraday(LocalDateTime.of(2026, 7, 1, 15, 30), "2870.10"));
        repository.recordRealtimeMinute(intraday(LocalDateTime.of(2026, 7, 2, 15, 30), "2890.12"));
        MarketIndexHistoryService service =
                new MarketIndexHistoryService(kisClient, yahooClient, repository, PRE_OPEN_CLOCK);

        assertThat(service.getPreviousClose("0001", LocalDate.of(2026, 7, 2)))
                .contains(new BigDecimal("2870.10"));
        verifyNoInteractions(kisClient, yahooClient);
    }

    @Test
    void getPreviousCloseBackfillsYahooPreviousSessionWhenStorageIsEmpty() {
        KisIndexMinuteChartPriceClient kisClient = mock(KisIndexMinuteChartPriceClient.class);
        YahooIndexMinuteChartPriceClient yahooClient = mock(YahooIndexMinuteChartPriceClient.class);
        InMemoryMarketIndexSnapshotRepository repository = new InMemoryMarketIndexSnapshotRepository();
        when(yahooClient.findMinutePrices("0001", LocalDate.of(2026, 7, 1), 600)).thenReturn(List.of(
                kisPrice(LocalDateTime.of(2026, 7, 1, 9, 0), "2860.00"),
                kisPrice(LocalDateTime.of(2026, 7, 1, 15, 30), "2870.10")));
        MarketIndexHistoryService service =
                new MarketIndexHistoryService(kisClient, yahooClient, repository, PRE_OPEN_CLOCK);

        assertThat(service.getPreviousClose("0001", LocalDate.of(2026, 7, 2)))
                .contains(new BigDecimal("2870.10"));
        assertThat(repository.findLatestBefore("0001", LocalDate.of(2026, 7, 2)))
                .map(MarketIndexIntradayPrice::closeValue)
                .contains(new BigDecimal("2870.10"));
        verifyNoInteractions(kisClient);
    }

    @Test
    void getIntradayHistoryDropsImplausibleProviderRows() {
        KisIndexMinuteChartPriceClient kisClient = mock(KisIndexMinuteChartPriceClient.class);
        InMemoryMarketIndexSnapshotRepository repository = new InMemoryMarketIndexSnapshotRepository();
        LocalDate explicitDate = LocalDate.of(2026, 7, 2);
        when(kisClient.findMinutePrices("0001", explicitDate, 390)).thenReturn(List.of(kisPrice(
                LocalDateTime.of(2026, 7, 2, 10, 46),
                "0.00")));
        MarketIndexHistoryService service = new MarketIndexHistoryService(kisClient, repository, PRE_OPEN_CLOCK);

        List<MarketIndexIntradayPrice> prices = service.getIntradayHistory("0001", explicitDate, 390);

        assertThat(prices).isEmpty();
        assertThat(repository.findIntraday("0001", explicitDate, 390)).isEmpty();
    }

    @Test
    void getIntradayHistoryReturnsEmptyWhenProviderFails() {
        KisIndexMinuteChartPriceClient kisClient = mock(KisIndexMinuteChartPriceClient.class);
        InMemoryMarketIndexSnapshotRepository repository = new InMemoryMarketIndexSnapshotRepository();
        LocalDate explicitDate = LocalDate.of(2026, 7, 2);
        when(kisClient.findMinutePrices("0001", explicitDate, 390))
                .thenThrow(new IllegalStateException("rate limited"));
        MarketIndexHistoryService service = new MarketIndexHistoryService(kisClient, repository, PRE_OPEN_CLOCK);

        List<MarketIndexIntradayPrice> prices = service.getIntradayHistory("0001", explicitDate, 390);

        assertThat(prices).isEmpty();
    }

    private MarketIndexIntradayPrice intraday(LocalDateTime bucketStart, String closeValue) {
        return intraday(bucketStart, closeValue, "KIS_REAL_INDEX_REALTIME");
    }

    private MarketIndexIntradayPrice intraday(LocalDateTime bucketStart, String closeValue, String source) {
        return new MarketIndexIntradayPrice(
                "0001",
                "KOSPI",
                "KOSPI",
                bucketStart,
                new BigDecimal("2880.00"),
                new BigDecimal("2895.00"),
                new BigDecimal("2875.00"),
                new BigDecimal(closeValue),
                1_200_000L,
                new BigDecimal("3400000000000"),
                source,
                Instant.now(PRE_OPEN_CLOCK));
    }

    private KisIndexMinuteChartPrice kisPrice(LocalDateTime bucketStart, String closeValue) {
        return new KisIndexMinuteChartPrice(
                bucketStart,
                new BigDecimal("2880.00"),
                new BigDecimal("2895.00"),
                new BigDecimal("2875.00"),
                new BigDecimal(closeValue),
                1_200_000L,
                new BigDecimal("3400000000000"));
    }
}

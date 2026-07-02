package com.hana.omnilens.market.application;

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

import com.hana.omnilens.market.domain.MarketIndexIntradayPrice;
import com.hana.omnilens.provider.market.KisIndexMinuteChartPrice;
import com.hana.omnilens.provider.market.KisIndexMinuteChartPriceClient;

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

    private MarketIndexIntradayPrice intraday(LocalDateTime bucketStart, String closeValue) {
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
                "KIS_REALTIME_INDEX",
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

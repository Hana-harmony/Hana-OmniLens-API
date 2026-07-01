package com.hana.omnilens.market.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.hana.omnilens.market.domain.ForeignOwnershipDailySnapshot;
import com.hana.omnilens.market.domain.StockSummary;
import com.hana.omnilens.provider.market.ForeignOwnershipHistoricalSnapshotClient;
import com.hana.omnilens.provider.market.ForeignOwnershipSnapshot;

class ForeignOwnershipRefreshServiceTest {

    private final StockMasterRepository stockMasterRepository = org.mockito.Mockito.mock(StockMasterRepository.class);
    private final ForeignOwnershipSnapshotCache cache = org.mockito.Mockito.mock(ForeignOwnershipSnapshotCache.class);
    private final ForeignOwnershipDailySnapshotRepository dailySnapshotRepository =
            org.mockito.Mockito.mock(ForeignOwnershipDailySnapshotRepository.class);
    private final MarketDailyPriceRepository marketDailyPriceRepository =
            org.mockito.Mockito.mock(MarketDailyPriceRepository.class);
    private final ForeignOwnershipHistoricalSnapshotClient historicalSnapshotClient =
            org.mockito.Mockito.mock(ForeignOwnershipHistoricalSnapshotClient.class);
    private final Clock clock = Clock.fixed(Instant.parse("2025-06-05T00:00:00Z"), ZoneOffset.UTC);
    private final ForeignOwnershipRefreshService service = new ForeignOwnershipRefreshService(
            stockMasterRepository,
            cache,
            dailySnapshotRepository,
            marketDailyPriceRepository,
            historicalSnapshotClient,
            clock);

    @Test
    void refreshStoresKrxForeignOwnershipSnapshotInCache() {
        ForeignOwnershipSnapshot snapshot = snapshot(LocalDate.of(2025, 6, 4));
        when(stockMasterRepository.findByCode("005930")).thenReturn(Optional.of(stock()));
        when(historicalSnapshotClient.findSnapshots(stock(), LocalDate.of(2025, 6, 4), LocalDate.of(2025, 6, 4)))
                .thenReturn(List.of(snapshot));

        ForeignOwnershipRefreshResult result = service.refresh("005930", LocalDate.of(2025, 6, 4));

        assertThat(result.refreshed()).isTrue();
        assertThat(result.snapshot()).contains(snapshot);
        assertThat(result.source()).isEqualTo("KRX_DATA_MARKETPLACE_FOREIGN_OWNERSHIP");
        verify(cache).put(snapshot);
        ArgumentCaptor<ForeignOwnershipDailySnapshot> dailySnapshotCaptor =
                ArgumentCaptor.forClass(ForeignOwnershipDailySnapshot.class);
        verify(dailySnapshotRepository).upsert(dailySnapshotCaptor.capture());
        assertThat(dailySnapshotCaptor.getValue().stockCode()).isEqualTo("005930");
        assertThat(dailySnapshotCaptor.getValue().baseDate()).isEqualTo(LocalDate.of(2025, 6, 4));
        assertThat(dailySnapshotCaptor.getValue().foreignLimitExhaustionRate()).isEqualByComparingTo("54.2100");
    }

    @Test
    void refreshUsesPreviousDayWhenBaseDateIsMissing() {
        when(stockMasterRepository.findByCode("005930")).thenReturn(Optional.of(stock()));
        when(historicalSnapshotClient.findSnapshots(stock(), LocalDate.of(2025, 6, 4), LocalDate.of(2025, 6, 4)))
                .thenReturn(List.of(snapshot(LocalDate.of(2025, 6, 4))));

        service.refresh("005930", null);

        ArgumentCaptor<ForeignOwnershipSnapshot> snapshotCaptor =
                ArgumentCaptor.forClass(ForeignOwnershipSnapshot.class);
        verify(cache).put(snapshotCaptor.capture());
        assertThat(snapshotCaptor.getValue().baseDate()).isEqualTo(LocalDate.of(2025, 6, 4));
    }

    @Test
    void refreshDoesNotOverwriteCacheWhenProviderReturnsEmpty() {
        when(stockMasterRepository.findByCode("005930")).thenReturn(Optional.of(stock()));
        when(historicalSnapshotClient.findSnapshots(stock(), LocalDate.of(2025, 6, 4), LocalDate.of(2025, 6, 4)))
                .thenReturn(List.of());

        ForeignOwnershipRefreshResult result = service.refresh("005930", LocalDate.of(2025, 6, 4));

        assertThat(result.refreshed()).isFalse();
        assertThat(result.snapshot()).isEmpty();
        verify(cache, never()).put(org.mockito.Mockito.any());
        verify(dailySnapshotRepository, never()).upsert(org.mockito.Mockito.any());
    }

    @Test
    void refreshDoesNotOverwriteCacheWhenProviderFails() {
        when(stockMasterRepository.findByCode("005930")).thenReturn(Optional.of(stock()));
        when(historicalSnapshotClient.findSnapshots(stock(), LocalDate.of(2025, 6, 4), LocalDate.of(2025, 6, 4)))
                .thenThrow(new IllegalStateException("KRX unavailable"));

        ForeignOwnershipRefreshResult result = service.refresh("005930", LocalDate.of(2025, 6, 4));

        assertThat(result.refreshed()).isFalse();
        assertThat(result.snapshot()).isEmpty();
        verify(cache, never()).put(org.mockito.Mockito.any());
        verify(dailySnapshotRepository, never()).upsert(org.mockito.Mockito.any());
    }

    @Test
    void collectDefaultsToRestrictedForeignOwnershipUniverseAndIsolatesProviderEmptyResults() {
        StockSummary kepco = restrictedStock("015760", "한국전력", "KEPCO");
        StockSummary kt = restrictedStock("030200", "KT", "KT");
        ForeignOwnershipSnapshot ktSnapshot = snapshot("030200", LocalDate.of(2025, 6, 4));
        when(stockMasterRepository.findByCode("015760")).thenReturn(Optional.of(kepco));
        when(stockMasterRepository.findByCode("030200")).thenReturn(Optional.of(kt));
        when(historicalSnapshotClient.findSnapshots(kt, LocalDate.of(2025, 6, 4), LocalDate.of(2025, 6, 4)))
                .thenReturn(List.of(ktSnapshot));
        when(historicalSnapshotClient.findSnapshots(kepco, LocalDate.of(2025, 6, 4), LocalDate.of(2025, 6, 4)))
                .thenReturn(List.of());

        ForeignOwnershipCollectionResult result = service.collect(LocalDate.of(2025, 6, 4), List.of(), 2);

        assertThat(result.baseDate()).isEqualTo(LocalDate.of(2025, 6, 4));
        assertThat(result.requestedCount()).isEqualTo(2);
        assertThat(result.refreshedCount()).isEqualTo(1);
        assertThat(result.failedCount()).isEqualTo(1);
        assertThat(result.status()).isEqualTo("PARTIAL");
        assertThat(result.stockResults())
                .extracting(ForeignOwnershipCollectionResult.StockResult::stockCode)
                .containsExactly("015760", "030200");
        assertThat(result.stockResults())
                .extracting(ForeignOwnershipCollectionResult.StockResult::status)
                .containsExactly("PROVIDER_EMPTY", "REFRESHED");
        verify(cache).put(ktSnapshot);
        verify(stockMasterRepository, never()).findAll(2);
    }

    @Test
    void collectReportsUnknownRequestedStockWithoutStoppingKnownStocks() {
        when(stockMasterRepository.findByCode("005930")).thenReturn(Optional.of(stock()));
        when(stockMasterRepository.findByCode("999999")).thenReturn(Optional.empty());
        when(historicalSnapshotClient.findSnapshots(stock(), LocalDate.of(2025, 6, 4), LocalDate.of(2025, 6, 4)))
                .thenReturn(List.of(snapshot(LocalDate.of(2025, 6, 4))));

        ForeignOwnershipCollectionResult result = service.collect(
                LocalDate.of(2025, 6, 4),
                List.of("999999", "005930", "005930"),
                100);

        assertThat(result.requestedCount()).isEqualTo(2);
        assertThat(result.refreshedCount()).isEqualTo(1);
        assertThat(result.failedCount()).isEqualTo(1);
        assertThat(result.status()).isEqualTo("PARTIAL");
        assertThat(result.stockResults())
                .extracting(ForeignOwnershipCollectionResult.StockResult::status)
                .containsExactly("STOCK_NOT_FOUND", "REFRESHED");
    }

    @Test
    void backfillMissingStoresOnlyMissingTradingDateHistoricalSnapshots() {
        StockSummary stock = stock();
        LocalDate fromDate = LocalDate.of(2025, 6, 2);
        LocalDate toDate = LocalDate.of(2025, 6, 6);
        when(stockMasterRepository.findByCode("005930")).thenReturn(Optional.of(stock));
        when(marketDailyPriceRepository.findTradingDates(fromDate, toDate))
                .thenReturn(List.of(
                        LocalDate.of(2025, 6, 2),
                        LocalDate.of(2025, 6, 3),
                        LocalDate.of(2025, 6, 4),
                        LocalDate.of(2025, 6, 5),
                        LocalDate.of(2025, 6, 6)));
        when(dailySnapshotRepository.findBaseDates("005930", fromDate, toDate))
                .thenReturn(List.of(LocalDate.of(2025, 6, 2), LocalDate.of(2025, 6, 4)));
        when(historicalSnapshotClient.findSnapshots(stock, LocalDate.of(2025, 6, 3), LocalDate.of(2025, 6, 6)))
                .thenReturn(List.of(
                        snapshot(LocalDate.of(2025, 6, 3)),
                        snapshot(LocalDate.of(2025, 6, 6)),
                        new ForeignOwnershipSnapshot(
                                "000660",
                                1_000L,
                                new BigDecimal("1.0000"),
                                2_000L,
                                new BigDecimal("50.0000"),
                                LocalDate.of(2025, 6, 5))));
        when(dailySnapshotRepository.upsert(org.mockito.Mockito.any(ForeignOwnershipDailySnapshot.class)))
                .thenReturn(1);

        ForeignOwnershipBackfillResult result = service.backfillMissing(
                fromDate,
                toDate,
                List.of("005930"),
                30,
                0L);

        assertThat(result.requestedStockCount()).isEqualTo(1);
        assertThat(result.missingDateCount()).isEqualTo(3);
        assertThat(result.savedCount()).isEqualTo(2);
        assertThat(result.failedDateCount()).isEqualTo(1);
        assertThat(result.status()).isEqualTo("PARTIAL");
        ArgumentCaptor<ForeignOwnershipDailySnapshot> dailySnapshotCaptor =
                ArgumentCaptor.forClass(ForeignOwnershipDailySnapshot.class);
        verify(dailySnapshotRepository, org.mockito.Mockito.times(2)).upsert(dailySnapshotCaptor.capture());
        assertThat(dailySnapshotCaptor.getAllValues())
                .extracting(ForeignOwnershipDailySnapshot::baseDate)
                .containsExactly(LocalDate.of(2025, 6, 3), LocalDate.of(2025, 6, 6));
    }

    @Test
    void backfillMissingDoesNotTreatWeekdayHolidayAsMissing() {
        StockSummary stock = stock();
        LocalDate fromDate = LocalDate.of(2025, 6, 2);
        LocalDate toDate = LocalDate.of(2025, 6, 6);
        when(stockMasterRepository.findByCode("005930")).thenReturn(Optional.of(stock));
        when(marketDailyPriceRepository.findTradingDates(fromDate, toDate))
                .thenReturn(List.of(
                        LocalDate.of(2025, 6, 2),
                        LocalDate.of(2025, 6, 3),
                        LocalDate.of(2025, 6, 4),
                        LocalDate.of(2025, 6, 5)));
        when(dailySnapshotRepository.findBaseDates("005930", fromDate, toDate))
                .thenReturn(List.of(
                        LocalDate.of(2025, 6, 2),
                        LocalDate.of(2025, 6, 3),
                        LocalDate.of(2025, 6, 4),
                        LocalDate.of(2025, 6, 5)));

        ForeignOwnershipBackfillResult result = service.backfillMissing(
                fromDate,
                toDate,
                List.of("005930"),
                30,
                0L);

        assertThat(result.status()).isEqualTo("EMPTY");
        assertThat(result.missingDateCount()).isZero();
        assertThat(result.failedDateCount()).isZero();
        assertThat(result.stockResults().get(0).status()).isEqualTo("SKIPPED");
        verify(historicalSnapshotClient, never())
                .findSnapshots(org.mockito.Mockito.any(), org.mockito.Mockito.any(), org.mockito.Mockito.any());
    }

    @Test
    void backfillMissingSkipsProviderWhenAllTradingDatesAlreadyExist() {
        StockSummary stock = stock();
        LocalDate fromDate = LocalDate.of(2025, 6, 2);
        LocalDate toDate = LocalDate.of(2025, 6, 6);
        when(stockMasterRepository.findByCode("005930")).thenReturn(Optional.of(stock));
        when(marketDailyPriceRepository.findTradingDates(fromDate, toDate))
                .thenReturn(List.of(
                        LocalDate.of(2025, 6, 2),
                        LocalDate.of(2025, 6, 3),
                        LocalDate.of(2025, 6, 4),
                        LocalDate.of(2025, 6, 5),
                        LocalDate.of(2025, 6, 6)));
        when(dailySnapshotRepository.findBaseDates("005930", fromDate, toDate))
                .thenReturn(List.of(
                        LocalDate.of(2025, 6, 2),
                        LocalDate.of(2025, 6, 3),
                        LocalDate.of(2025, 6, 4),
                        LocalDate.of(2025, 6, 5),
                        LocalDate.of(2025, 6, 6)));

        ForeignOwnershipBackfillResult result = service.backfillMissing(
                fromDate,
                toDate,
                List.of("005930"),
                30,
                0L);

        assertThat(result.status()).isEqualTo("EMPTY");
        assertThat(result.stockResults().get(0).status()).isEqualTo("SKIPPED");
        verify(historicalSnapshotClient, never())
                .findSnapshots(org.mockito.Mockito.any(), org.mockito.Mockito.any(), org.mockito.Mockito.any());
    }

    @Test
    void backfillMissingSplitsLongHistoricalRangeForKrxPeriodLimit() {
        StockSummary stock = stock();
        LocalDate fromDate = LocalDate.of(2024, 1, 2);
        LocalDate toDate = LocalDate.of(2025, 6, 30);
        when(stockMasterRepository.findByCode("005930")).thenReturn(Optional.of(stock));
        when(marketDailyPriceRepository.findTradingDates(fromDate, toDate))
                .thenReturn(tradingDates(fromDate, toDate));
        when(dailySnapshotRepository.findBaseDates("005930", fromDate, toDate))
                .thenReturn(List.of());
        when(historicalSnapshotClient.findSnapshots(
                stock,
                LocalDate.of(2024, 1, 2),
                LocalDate.of(2024, 12, 27)))
                .thenReturn(List.of());
        when(historicalSnapshotClient.findSnapshots(
                stock,
                LocalDate.of(2024, 12, 30),
                LocalDate.of(2025, 6, 30)))
                .thenReturn(List.of());

        ForeignOwnershipBackfillResult result = service.backfillMissing(
                fromDate,
                toDate,
                List.of("005930"),
                30,
                0L);

        assertThat(result.requestedStockCount()).isEqualTo(1);
        verify(historicalSnapshotClient).findSnapshots(
                stock,
                LocalDate.of(2024, 1, 2),
                LocalDate.of(2024, 12, 27));
        verify(historicalSnapshotClient).findSnapshots(
                stock,
                LocalDate.of(2024, 12, 30),
                LocalDate.of(2025, 6, 30));
    }

    private StockSummary stock() {
        return new StockSummary(
                "005930",
                "삼성전자",
                "Samsung Electronics",
                "KOSPI",
                "KR7005930003",
                "00126380");
    }

    private StockSummary restrictedStock(String stockCode, String stockName, String stockNameEn) {
        return new StockSummary(
                stockCode,
                stockName,
                stockNameEn,
                "KOSPI",
                "KR7" + stockCode + "000",
                "");
    }

    private ForeignOwnershipSnapshot snapshot(LocalDate baseDate) {
        return snapshot("005930", baseDate);
    }

    private ForeignOwnershipSnapshot snapshot(String stockCode, LocalDate baseDate) {
        return new ForeignOwnershipSnapshot(
                stockCode,
                3_642_091_300L,
                new BigDecimal("61.0088"),
                6_718_486_073L,
                new BigDecimal("54.2100"),
                baseDate);
    }

    private List<LocalDate> tradingDates(LocalDate fromDate, LocalDate toDate) {
        List<LocalDate> dates = new ArrayList<>();
        LocalDate date = fromDate;
        while (!date.isAfter(toDate)) {
            if (date.getDayOfWeek() != DayOfWeek.SATURDAY
                    && date.getDayOfWeek() != DayOfWeek.SUNDAY) {
                dates.add(date);
            }
            date = date.plusDays(1);
        }
        return dates;
    }

}

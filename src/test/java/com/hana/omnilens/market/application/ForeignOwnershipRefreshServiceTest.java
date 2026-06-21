package com.hana.omnilens.market.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.client.RestClientException;

import com.hana.omnilens.market.domain.ForeignOwnershipDailySnapshot;
import com.hana.omnilens.market.domain.StockSummary;
import com.hana.omnilens.provider.market.ForeignOwnershipSnapshot;
import com.hana.omnilens.provider.market.KisCurrentPriceClient;
import com.hana.omnilens.provider.market.KisCurrentPriceSnapshot;

class ForeignOwnershipRefreshServiceTest {

    private final KisCurrentPriceClient kisCurrentPriceClient = org.mockito.Mockito.mock(KisCurrentPriceClient.class);
    private final StockMasterRepository stockMasterRepository = org.mockito.Mockito.mock(StockMasterRepository.class);
    private final ForeignOwnershipSnapshotCache cache = org.mockito.Mockito.mock(ForeignOwnershipSnapshotCache.class);
    private final ForeignOwnershipDailySnapshotRepository dailySnapshotRepository =
            org.mockito.Mockito.mock(ForeignOwnershipDailySnapshotRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2025-06-05T00:00:00Z"), ZoneOffset.UTC);
    private final ForeignOwnershipRefreshService service = new ForeignOwnershipRefreshService(
            kisCurrentPriceClient,
            stockMasterRepository,
            cache,
            dailySnapshotRepository,
            clock);

    @Test
    void refreshStoresKisForeignOwnershipSnapshotInCache() {
        ForeignOwnershipSnapshot snapshot = snapshot(LocalDate.of(2025, 6, 4));
        when(stockMasterRepository.findByCode("005930")).thenReturn(Optional.of(stock()));
        when(kisCurrentPriceClient.findCurrentPrice("005930")).thenReturn(Optional.of(kisSnapshot()));

        ForeignOwnershipRefreshResult result = service.refresh("005930", LocalDate.of(2025, 6, 4));

        assertThat(result.refreshed()).isTrue();
        assertThat(result.snapshot()).contains(snapshot);
        assertThat(result.source()).isEqualTo("KIS_CURRENT_PRICE_FOREIGN_OWNERSHIP");
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
        when(kisCurrentPriceClient.findCurrentPrice("005930")).thenReturn(Optional.of(kisSnapshot()));

        service.refresh("005930", null);

        ArgumentCaptor<ForeignOwnershipSnapshot> snapshotCaptor =
                ArgumentCaptor.forClass(ForeignOwnershipSnapshot.class);
        verify(cache).put(snapshotCaptor.capture());
        assertThat(snapshotCaptor.getValue().baseDate()).isEqualTo(LocalDate.of(2025, 6, 4));
    }

    @Test
    void refreshDoesNotOverwriteCacheWhenProviderReturnsEmpty() {
        when(stockMasterRepository.findByCode("005930")).thenReturn(Optional.of(stock()));
        when(kisCurrentPriceClient.findCurrentPrice("005930")).thenReturn(Optional.empty());

        ForeignOwnershipRefreshResult result = service.refresh("005930", LocalDate.of(2025, 6, 4));

        assertThat(result.refreshed()).isFalse();
        assertThat(result.snapshot()).isEmpty();
        verify(cache, never()).put(org.mockito.Mockito.any());
        verify(dailySnapshotRepository, never()).upsert(org.mockito.Mockito.any());
    }

    @Test
    void refreshDoesNotOverwriteCacheWhenProviderFails() {
        when(stockMasterRepository.findByCode("005930")).thenReturn(Optional.of(stock()));
        when(kisCurrentPriceClient.findCurrentPrice("005930")).thenThrow(new RestClientException("KIS unavailable"));

        ForeignOwnershipRefreshResult result = service.refresh("005930", LocalDate.of(2025, 6, 4));

        assertThat(result.refreshed()).isFalse();
        assertThat(result.snapshot()).isEmpty();
        verify(cache, never()).put(org.mockito.Mockito.any());
        verify(dailySnapshotRepository, never()).upsert(org.mockito.Mockito.any());
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

    private ForeignOwnershipSnapshot snapshot(LocalDate baseDate) {
        return new ForeignOwnershipSnapshot(
                "005930",
                3_642_091_300L,
                new BigDecimal("61.0088"),
                6_718_486_073L,
                new BigDecimal("54.2100"),
                baseDate);
    }

    private KisCurrentPriceSnapshot kisSnapshot() {
        return new KisCurrentPriceSnapshot(
                "005930",
                "삼성전자",
                new BigDecimal("81200"),
                new BigDecimal("1.87"),
                15_500_000L,
                3_642_091_300L,
                new BigDecimal("61.008777"),
                6_718_486_073L,
                new BigDecimal("54.21"));
    }
}

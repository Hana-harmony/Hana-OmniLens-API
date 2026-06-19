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

import com.hana.omnilens.market.domain.StockSummary;
import com.hana.omnilens.provider.market.KrxForeignOwnershipClient;
import com.hana.omnilens.provider.market.KrxForeignOwnershipSnapshot;

class ForeignOwnershipRefreshServiceTest {

    private final KrxForeignOwnershipClient krxForeignOwnershipClient = org.mockito.Mockito.mock(KrxForeignOwnershipClient.class);
    private final StockMasterRepository stockMasterRepository = org.mockito.Mockito.mock(StockMasterRepository.class);
    private final ForeignOwnershipSnapshotCache cache = org.mockito.Mockito.mock(ForeignOwnershipSnapshotCache.class);
    private final Clock clock = Clock.fixed(Instant.parse("2025-06-05T00:00:00Z"), ZoneOffset.UTC);
    private final ForeignOwnershipRefreshService service = new ForeignOwnershipRefreshService(
            krxForeignOwnershipClient,
            stockMasterRepository,
            cache,
            clock);

    @Test
    void refreshStoresKrxForeignOwnershipSnapshotInCache() {
        StockSummary stock = stock();
        KrxForeignOwnershipSnapshot snapshot = snapshot(LocalDate.of(2025, 6, 4));
        when(stockMasterRepository.findByCode("005930")).thenReturn(Optional.of(stock));
        when(krxForeignOwnershipClient.findForeignOwnership(
                "005930",
                "삼성전자",
                "KR7005930003",
                LocalDate.of(2025, 6, 4)))
                .thenReturn(Optional.of(snapshot));

        ForeignOwnershipRefreshResult result = service.refresh("005930", LocalDate.of(2025, 6, 4));

        assertThat(result.refreshed()).isTrue();
        assertThat(result.snapshot()).contains(snapshot);
        assertThat(result.source()).isEqualTo("KRX_FOREIGN_OWNERSHIP");
        verify(cache).put(snapshot);
    }

    @Test
    void refreshUsesPreviousDayWhenBaseDateIsMissing() {
        StockSummary stock = stock();
        when(stockMasterRepository.findByCode("005930")).thenReturn(Optional.of(stock));
        when(krxForeignOwnershipClient.findForeignOwnership(
                "005930",
                "삼성전자",
                "KR7005930003",
                LocalDate.of(2025, 6, 4)))
                .thenReturn(Optional.of(snapshot(LocalDate.of(2025, 6, 4))));

        service.refresh("005930", null);

        ArgumentCaptor<LocalDate> dateCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(krxForeignOwnershipClient).findForeignOwnership(
                org.mockito.Mockito.eq("005930"),
                org.mockito.Mockito.eq("삼성전자"),
                org.mockito.Mockito.eq("KR7005930003"),
                dateCaptor.capture());
        assertThat(dateCaptor.getValue()).isEqualTo(LocalDate.of(2025, 6, 4));
    }

    @Test
    void refreshDoesNotOverwriteCacheWhenProviderReturnsEmpty() {
        when(stockMasterRepository.findByCode("005930")).thenReturn(Optional.of(stock()));
        when(krxForeignOwnershipClient.findForeignOwnership(
                "005930",
                "삼성전자",
                "KR7005930003",
                LocalDate.of(2025, 6, 4)))
                .thenReturn(Optional.empty());

        ForeignOwnershipRefreshResult result = service.refresh("005930", LocalDate.of(2025, 6, 4));

        assertThat(result.refreshed()).isFalse();
        assertThat(result.snapshot()).isEmpty();
        verify(cache, never()).put(org.mockito.Mockito.any());
    }

    @Test
    void refreshDoesNotOverwriteCacheWhenProviderFails() {
        when(stockMasterRepository.findByCode("005930")).thenReturn(Optional.of(stock()));
        when(krxForeignOwnershipClient.findForeignOwnership(
                "005930",
                "삼성전자",
                "KR7005930003",
                LocalDate.of(2025, 6, 4)))
                .thenThrow(new RestClientException("LOGOUT"));

        ForeignOwnershipRefreshResult result = service.refresh("005930", LocalDate.of(2025, 6, 4));

        assertThat(result.refreshed()).isFalse();
        assertThat(result.snapshot()).isEmpty();
        verify(cache, never()).put(org.mockito.Mockito.any());
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

    private KrxForeignOwnershipSnapshot snapshot(LocalDate baseDate) {
        return new KrxForeignOwnershipSnapshot(
                "005930",
                3_642_091_300L,
                new BigDecimal("54.19"),
                6_720_000_000L,
                new BigDecimal("54.21"),
                baseDate);
    }
}

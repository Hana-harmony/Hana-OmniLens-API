package com.hana.omniconnect.alert.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.hana.omniconnect.alert.api.AlertCollectPublishRequest;
import com.hana.omniconnect.alert.api.AlertCollectPublishResponse;
import com.hana.omniconnect.config.AlertCollectionSchedulerProperties;
import com.hana.omniconnect.config.AlertCollectionSchedulerProperties.PartnerWatchlist;

class AlertCollectionSchedulerTest {

    private final AlertProviderCollectionService collectionService = mock(AlertProviderCollectionService.class);
    private final PartnerWatchlistRepository watchlistRepository = mock(PartnerWatchlistRepository.class);
    private final AlertCollectionTargetUniverseProvider targetUniverseProvider =
            mock(AlertCollectionTargetUniverseProvider.class);

    @Test
    void skipsCollectionWhenSchedulerIsDisabled() {
        AlertCollectionScheduler scheduler = new AlertCollectionScheduler(
                collectionService,
                schedulerProperties(
                        false,
                        60_000L,
                        5,
                        3,
                        List.of(new PartnerWatchlist("partner-a", List.of("005930")))),
                watchlistRepository,
                targetUniverseProvider);

        scheduler.collectConfiguredWatchlists();

        verify(collectionService, never()).collectIncrementalAnalyzeAndPublish(any());
        verify(watchlistRepository, never()).findAll();
    }

    @Test
    void collectsConfiguredPartnerWatchlists() {
        when(watchlistRepository.findAll()).thenReturn(List.of());
        when(collectionService.collectIncrementalAnalyzeAndPublish(any()))
                .thenReturn(new AlertCollectPublishResponse(
                        "partner-a",
                        List.of("005930", "000660"),
                        2,
                        1,
                        3,
                        0,
                        0,
                        List.of()));
        AlertCollectionScheduler scheduler = new AlertCollectionScheduler(
                collectionService,
                schedulerProperties(
                        true,
                        60_000L,
                        7,
                        5,
                        List.of(new PartnerWatchlist("partner-a", List.of("005930", "000660")))),
                watchlistRepository,
                targetUniverseProvider);

        scheduler.collectConfiguredWatchlists();

        ArgumentCaptor<AlertCollectPublishRequest> requestCaptor =
                ArgumentCaptor.forClass(AlertCollectPublishRequest.class);
        verify(collectionService).collectIncrementalAnalyzeAndPublish(requestCaptor.capture());
        AlertCollectPublishRequest request = requestCaptor.getValue();
        assertThat(request.partnerId()).isEqualTo("partner-a");
        assertThat(request.stockCodes()).containsExactly("005930", "000660");
        assertThat(request.newsDisplay()).isEqualTo(7);
        assertThat(request.disclosureLookbackDays()).isEqualTo(5);
    }

    @Test
    void mergesConfiguredAndStoredPartnerWatchlists() {
        when(watchlistRepository.findAll()).thenReturn(List.of(
                new com.hana.omniconnect.alert.application.PartnerWatchlist(
                        "partner-a",
                        List.of("000660", "035420")),
                new com.hana.omniconnect.alert.application.PartnerWatchlist(
                        "partner-b",
                        List.of("005380"))));
        when(collectionService.collectIncrementalAnalyzeAndPublish(any()))
                .thenReturn(new AlertCollectPublishResponse(
                        "partner-a",
                        List.of("005930", "000660", "035420"),
                        3,
                        0,
                        3,
                        0,
                        0,
                        List.of()));
        AlertCollectionScheduler scheduler = new AlertCollectionScheduler(
                collectionService,
                schedulerProperties(
                        true,
                        60_000L,
                        10,
                        7,
                        List.of(new PartnerWatchlist("partner-a", List.of("005930", "000660")))),
                watchlistRepository,
                targetUniverseProvider);

        scheduler.collectConfiguredWatchlists();

        ArgumentCaptor<AlertCollectPublishRequest> requestCaptor =
                ArgumentCaptor.forClass(AlertCollectPublishRequest.class);
        verify(collectionService, times(2)).collectIncrementalAnalyzeAndPublish(requestCaptor.capture());
        assertThat(requestCaptor.getAllValues())
                .extracting(AlertCollectPublishRequest::partnerId)
                .containsExactly("partner-a", "partner-b");
        assertThat(requestCaptor.getAllValues().get(0).stockCodes())
                .containsExactly("005930", "000660", "035420");
        assertThat(requestCaptor.getAllValues().get(1).stockCodes())
                .containsExactly("005380");
    }

    @Test
    void continuesAfterPartnerCollectionFailure() {
        when(watchlistRepository.findAll()).thenReturn(List.of());
        when(collectionService.collectIncrementalAnalyzeAndPublish(any()))
                .thenThrow(new IllegalStateException("provider unavailable"))
                .thenReturn(new AlertCollectPublishResponse(
                        "partner-b",
                        List.of("000660"),
                        1,
                        0,
                        1,
                        0,
                        0,
                        List.of()));
        AlertCollectionScheduler scheduler = new AlertCollectionScheduler(
                collectionService,
                schedulerProperties(
                        true,
                        60_000L,
                        10,
                        7,
                        List.of(
                                new PartnerWatchlist("partner-a", List.of("005930")),
                                new PartnerWatchlist("partner-b", List.of("000660")))),
                watchlistRepository,
                targetUniverseProvider);

        scheduler.collectConfiguredWatchlists();

        ArgumentCaptor<AlertCollectPublishRequest> requestCaptor =
                ArgumentCaptor.forClass(AlertCollectPublishRequest.class);
        verify(collectionService, times(2)).collectIncrementalAnalyzeAndPublish(requestCaptor.capture());
        assertThat(requestCaptor.getAllValues())
                .extracting(AlertCollectPublishRequest::partnerId)
                .containsExactly("partner-a", "partner-b");
    }

    @Test
    void collectsDefaultPriorityAndForeignOwnershipUniverse() {
        when(watchlistRepository.findAll()).thenReturn(List.of());
        when(targetUniverseProvider.defaultStockCodes(30, true))
                .thenReturn(List.of("005930", "000660", "015760"));
        when(collectionService.collectIncrementalAnalyzeAndPublish(any()))
                .thenReturn(new AlertCollectPublishResponse(
                        "omni-connect-default-universe",
                        List.of("005930", "000660", "015760"),
                        3,
                        1,
                        4,
                        0,
                        0,
                        List.of()));
        AlertCollectionScheduler scheduler = new AlertCollectionScheduler(
                collectionService,
                new AlertCollectionSchedulerProperties(
                        true,
                        60_000L,
                        60_000L,
                        10,
                        7,
                        List.of(),
                        true,
                        "omni-connect-default-universe",
                        30,
                        true,
                        20),
                watchlistRepository,
                targetUniverseProvider);

        scheduler.collectConfiguredWatchlists();

        ArgumentCaptor<AlertCollectPublishRequest> requestCaptor =
                ArgumentCaptor.forClass(AlertCollectPublishRequest.class);
        verify(collectionService).collectIncrementalAnalyzeAndPublish(requestCaptor.capture());
        AlertCollectPublishRequest request = requestCaptor.getValue();
        assertThat(request.partnerId()).isEqualTo("omni-connect-default-universe");
        assertThat(request.stockCodes()).containsExactly("005930", "000660", "015760");
    }

    @Test
    void splitsDefaultUniverseIntoApiSizedBatches() {
        List<String> stockCodes = java.util.stream.IntStream.rangeClosed(1, 23)
                .mapToObj(value -> String.format("%06d", value))
                .toList();
        when(watchlistRepository.findAll()).thenReturn(List.of());
        when(targetUniverseProvider.defaultStockCodes(30, true)).thenReturn(stockCodes);
        when(collectionService.collectIncrementalAnalyzeAndPublish(any()))
                .thenReturn(new AlertCollectPublishResponse(
                        "omni-connect-default-universe",
                        stockCodes,
                        0,
                        0,
                        0,
                        0,
                        0,
                        List.of()));
        AlertCollectionScheduler scheduler = new AlertCollectionScheduler(
                collectionService,
                new AlertCollectionSchedulerProperties(
                        true,
                        60_000L,
                        60_000L,
                        10,
                        7,
                        List.of(),
                        true,
                        "omni-connect-default-universe",
                        30,
                        true,
                        20),
                watchlistRepository,
                targetUniverseProvider);

        scheduler.collectConfiguredWatchlists();

        ArgumentCaptor<AlertCollectPublishRequest> requestCaptor =
                ArgumentCaptor.forClass(AlertCollectPublishRequest.class);
        verify(collectionService, times(2)).collectIncrementalAnalyzeAndPublish(requestCaptor.capture());
        assertThat(requestCaptor.getAllValues().get(0).stockCodes()).hasSize(20);
        assertThat(requestCaptor.getAllValues().get(1).stockCodes()).hasSize(3);
    }

    @Test
    void continuesAfterDefaultUniverseBatchFailure() {
        List<String> stockCodes = java.util.stream.IntStream.rangeClosed(1, 23)
                .mapToObj(value -> String.format("%06d", value))
                .toList();
        when(watchlistRepository.findAll()).thenReturn(List.of());
        when(targetUniverseProvider.defaultStockCodes(30, true)).thenReturn(stockCodes);
        when(collectionService.collectIncrementalAnalyzeAndPublish(any()))
                .thenThrow(new IllegalStateException("provider unavailable"))
                .thenReturn(new AlertCollectPublishResponse(
                        "omni-connect-default-universe",
                        stockCodes.subList(20, 23),
                        0,
                        0,
                        0,
                        0,
                        0,
                        List.of()));
        AlertCollectionScheduler scheduler = new AlertCollectionScheduler(
                collectionService,
                new AlertCollectionSchedulerProperties(
                        true,
                        60_000L,
                        60_000L,
                        10,
                        7,
                        List.of(),
                        true,
                        "omni-connect-default-universe",
                        30,
                        true,
                        20),
                watchlistRepository,
                targetUniverseProvider);

        scheduler.collectConfiguredWatchlists();

        verify(collectionService, times(2)).collectIncrementalAnalyzeAndPublish(any());
    }

    private static AlertCollectionSchedulerProperties schedulerProperties(
            boolean enabled,
            long fixedDelayMs,
            int newsDisplay,
            int disclosureLookbackDays,
            List<PartnerWatchlist> watchlists) {
        return new AlertCollectionSchedulerProperties(
                enabled,
                fixedDelayMs,
                60_000L,
                newsDisplay,
                disclosureLookbackDays,
                watchlists,
                false,
                "omni-connect-default-universe",
                30,
                false,
                20);
    }
}

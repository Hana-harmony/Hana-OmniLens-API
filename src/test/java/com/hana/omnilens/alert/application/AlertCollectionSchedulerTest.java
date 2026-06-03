package com.hana.omnilens.alert.application;

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

import com.hana.omnilens.alert.api.AlertCollectPublishRequest;
import com.hana.omnilens.alert.api.AlertCollectPublishResponse;
import com.hana.omnilens.config.AlertCollectionSchedulerProperties;
import com.hana.omnilens.config.AlertCollectionSchedulerProperties.PartnerWatchlist;

class AlertCollectionSchedulerTest {

    private final AlertProviderCollectionService collectionService = mock(AlertProviderCollectionService.class);

    @Test
    void skipsCollectionWhenSchedulerIsDisabled() {
        AlertCollectionScheduler scheduler = new AlertCollectionScheduler(
                collectionService,
                new AlertCollectionSchedulerProperties(
                        false,
                        60_000L,
                        5,
                        3,
                        List.of(new PartnerWatchlist("partner-a", List.of("005930")))));

        scheduler.collectConfiguredWatchlists();

        verify(collectionService, never()).collectAnalyzeAndPublish(any());
    }

    @Test
    void collectsConfiguredPartnerWatchlists() {
        when(collectionService.collectAnalyzeAndPublish(any()))
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
                new AlertCollectionSchedulerProperties(
                        true,
                        60_000L,
                        7,
                        5,
                        List.of(new PartnerWatchlist("partner-a", List.of("005930", "000660")))));

        scheduler.collectConfiguredWatchlists();

        ArgumentCaptor<AlertCollectPublishRequest> requestCaptor =
                ArgumentCaptor.forClass(AlertCollectPublishRequest.class);
        verify(collectionService).collectAnalyzeAndPublish(requestCaptor.capture());
        AlertCollectPublishRequest request = requestCaptor.getValue();
        assertThat(request.partnerId()).isEqualTo("partner-a");
        assertThat(request.stockCodes()).containsExactly("005930", "000660");
        assertThat(request.newsDisplay()).isEqualTo(7);
        assertThat(request.disclosureLookbackDays()).isEqualTo(5);
    }

    @Test
    void continuesAfterPartnerCollectionFailure() {
        when(collectionService.collectAnalyzeAndPublish(any()))
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
                new AlertCollectionSchedulerProperties(
                        true,
                        60_000L,
                        10,
                        7,
                        List.of(
                                new PartnerWatchlist("partner-a", List.of("005930")),
                                new PartnerWatchlist("partner-b", List.of("000660")))));

        scheduler.collectConfiguredWatchlists();

        ArgumentCaptor<AlertCollectPublishRequest> requestCaptor =
                ArgumentCaptor.forClass(AlertCollectPublishRequest.class);
        verify(collectionService, times(2)).collectAnalyzeAndPublish(requestCaptor.capture());
        assertThat(requestCaptor.getAllValues())
                .extracting(AlertCollectPublishRequest::partnerId)
                .containsExactly("partner-a", "partner-b");
    }
}

package com.hana.omniconnect.alert.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.hana.omniconnect.alert.api.AlertAnalysisPublishRequest;
import com.hana.omniconnect.alert.api.AlertPublishRequest;
import com.hana.omniconnect.alert.domain.AlertEvent;
import com.hana.omniconnect.market.application.StockMasterRepository;
import com.hana.omniconnect.market.domain.StockSummary;
import com.hana.omniconnect.provider.disclosure.OpenDartDisclosureClient;
import com.hana.omniconnect.provider.disclosure.OpenDartDisclosureDocument;

class DisclosureProcessingServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-22T10:00:00Z");

    private final DisclosureProcessingRepository processingRepository = mock(DisclosureProcessingRepository.class);
    private final OpenDartDisclosureClient openDartDisclosureClient = mock(OpenDartDisclosureClient.class);
    private final StockMasterRepository stockMasterRepository = mock(StockMasterRepository.class);
    private final AlertAnalysisPublishingService publishingService = mock(AlertAnalysisPublishingService.class);
    private final AlertEventRepository alertEventRepository = mock(AlertEventRepository.class);
    private final DisclosureProcessingService processingService = new DisclosureProcessingService(
            processingRepository,
            openDartDisclosureClient,
            stockMasterRepository,
            publishingService,
            alertEventRepository,
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void persistsOfficialDocumentBeforeQwenAndPublishesOnlyReadyOutput() {
        DisclosureProcessingJob job = claimedJob(null, 1);
        StockSummary stock = stock();
        String fullDocument = "SK하이닉스 투자설명서의 발행조건과 투자위험을 설명한다. ".repeat(100_000);
        AlertPublishRequest analyzed = mock(AlertPublishRequest.class);
        AlertEvent event = mock(AlertEvent.class);

        when(processingRepository.claimNext(NOW, Duration.ofMinutes(45))).thenReturn(Optional.of(job));
        when(stockMasterRepository.findByCode(job.stockCode())).thenReturn(Optional.of(stock));
        when(openDartDisclosureClient.fetchDocumentContent(job.receiptNumber()))
                .thenReturn(Optional.of(new OpenDartDisclosureDocument(
                        fullDocument,
                        "document-hash",
                        OpenDartDisclosureClient.OPENDART_PUBLIC_DISCLOSURE_TEXT)));
        when(publishingService.analyzeForCollection(any())).thenReturn(analyzed);
        when(analyzed.stockCode()).thenReturn(job.stockCode());
        when(publishingService.isPublishReady(analyzed)).thenReturn(true);
        when(publishingService.publishAnalyzed(analyzed)).thenReturn(event);
        when(event.alertId()).thenReturn("alert-1");

        Optional<AlertEvent> result = processingService.processNext();

        assertThat(result).contains(event);
        verify(processingRepository).saveSourceDocument(
                job,
                fullDocument,
                "document-hash",
                OpenDartDisclosureClient.OPENDART_PUBLIC_DISCLOSURE_TEXT,
                NOW);
        ArgumentCaptor<AlertAnalysisPublishRequest> requestCaptor =
                ArgumentCaptor.forClass(AlertAnalysisPublishRequest.class);
        verify(publishingService).analyzeForCollection(requestCaptor.capture());
        assertThat(requestCaptor.getValue().content()).hasSizeLessThanOrEqualTo(1_200);
        assertThat(requestCaptor.getValue().sourceLicensePolicy()).endsWith(":feed-excerpt-v1");
        verify(processingRepository).markReady(job, "alert-1", NOW);
    }

    @Test
    void keepsSourceAndSchedulesRetryWhenQwenFails() {
        DisclosureProcessingJob job = claimedJob("보존된 DART 공시 원문입니다.", 1);
        when(processingRepository.claimNext(NOW, Duration.ofMinutes(45))).thenReturn(Optional.of(job));
        when(stockMasterRepository.findByCode(job.stockCode())).thenReturn(Optional.of(stock()));
        when(publishingService.analyzeForCollection(any()))
                .thenThrow(new IllegalStateException("Qwen quality gate failed"));

        assertThat(processingService.processNext()).isEmpty();

        verify(openDartDisclosureClient, never()).fetchDocumentContent(any());
        verify(processingRepository).scheduleRetry(
                eq(job),
                eq("IllegalStateException: Qwen quality gate failed"),
                eq(NOW.plus(Duration.ofMinutes(1))),
                eq(NOW));
        verify(processingRepository, never()).markReady(any(), any(), any());
    }

    @Test
    void marksExistingPublishReadyDisclosureWithoutCallingQwenAgain() {
        DisclosureProcessingJob job = claimedJob(null, 2);
        AlertEvent existing = mock(AlertEvent.class);
        when(existing.alertId()).thenReturn("existing-alert");
        when(processingRepository.claimNext(NOW, Duration.ofMinutes(45))).thenReturn(Optional.of(job));
        when(alertEventRepository.findBySourceIdentity(
                job.partnerId(), job.stockCode(), "DISCLOSURE", job.originalUrl()))
                .thenReturn(Optional.of(existing));
        when(publishingService.isDisplayableFullArticle(existing)).thenReturn(true);

        assertThat(processingService.processNext()).contains(existing);

        verify(processingRepository).markReady(job, "existing-alert", NOW);
        verify(openDartDisclosureClient, never()).fetchDocumentContent(any());
        verify(publishingService, never()).analyzeForCollection(any());
    }

    private DisclosureProcessingJob claimedJob(String sourceContent, int attemptCount) {
        return new DisclosureProcessingJob(
                "job-1",
                "omni-connect-default-universe",
                "000660",
                "20260710000012",
                "SK하이닉스",
                "투자설명서",
                "https://dart.fss.or.kr/dsaf001/main.do?rcpNo=20260710000012",
                Instant.parse("2026-07-10T00:00:00Z"),
                sourceContent,
                "document-hash",
                OpenDartDisclosureClient.OPENDART_PUBLIC_DISCLOSURE_TEXT,
                "PROCESSING",
                attemptCount,
                "lease-token");
    }

    private StockSummary stock() {
        return new StockSummary(
                "000660",
                "SK하이닉스",
                "SK hynix",
                "KOSPI",
                "KR7000660001",
                "00164779");
    }
}

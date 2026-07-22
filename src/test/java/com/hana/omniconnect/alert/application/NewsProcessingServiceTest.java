package com.hana.omniconnect.alert.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.hana.omniconnect.alert.api.AlertPublishRequest;
import com.hana.omniconnect.alert.domain.AlertEvent;
import com.hana.omniconnect.market.application.StockMasterRepository;
import com.hana.omniconnect.market.domain.StockSummary;

class NewsProcessingServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-22T10:00:00Z");
    private final NewsProcessingRepository repository = mock(NewsProcessingRepository.class);
    private final StockMasterRepository stockRepository = mock(StockMasterRepository.class);
    private final AlertAnalysisPublishingService publishingService = mock(AlertAnalysisPublishingService.class);
    private final AlertEventRepository eventRepository = mock(AlertEventRepository.class);
    private final NewsProcessingService service = new NewsProcessingService(
            repository, stockRepository, publishingService, eventRepository,
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void publishesClaimedNewsAndMarksJobReady() {
        NewsProcessingJob job = claimedJob(1);
        StockSummary stock = new StockSummary(
                "030200", "KT", "KT", "KOSPI", "KR7030200000", "00190321");
        AlertPublishRequest analyzed = mock(AlertPublishRequest.class);
        AlertEvent event = mock(AlertEvent.class);
        when(repository.claimNext(NOW, Duration.ofMinutes(45))).thenReturn(Optional.of(job));
        when(stockRepository.findByCode(job.stockCode())).thenReturn(Optional.of(stock));
        when(publishingService.analyzeForCollection(any())).thenReturn(analyzed);
        when(analyzed.stockCode()).thenReturn(job.stockCode());
        when(publishingService.isPublishReady(analyzed)).thenReturn(true);
        when(publishingService.publishAnalyzed(analyzed)).thenReturn(event);
        when(event.alertId()).thenReturn("alert-news-1");

        assertThat(service.processNext()).contains(event);

        verify(repository).markReady(job, "alert-news-1", NOW);
    }

    @Test
    void schedulesRetryWithoutLosingArticleWhenQwenFails() {
        NewsProcessingJob job = claimedJob(1);
        when(repository.claimNext(NOW, Duration.ofMinutes(45))).thenReturn(Optional.of(job));
        when(stockRepository.findByCode(job.stockCode())).thenReturn(Optional.of(new StockSummary(
                "030200", "KT", "KT", "KOSPI", "KR7030200000", "00190321")));
        when(publishingService.analyzeForCollection(any()))
                .thenThrow(new IllegalStateException("Qwen unavailable"));

        assertThat(service.processNext()).isEmpty();

        verify(repository).scheduleRetry(
                eq(job), eq("IllegalStateException: Qwen unavailable"),
                eq(NOW.plus(Duration.ofMinutes(1))), eq(NOW));
    }

    private NewsProcessingJob claimedJob(int attemptCount) {
        return new NewsProcessingJob(
                "job-1", "omni-connect-default-universe", "030200",
                "KT 주가 강세", "KT 실적 기사", "https://news.example.com/kt", NOW.minusSeconds(60),
                "KT의 실적과 주가를 다룬 완전한 기사 본문입니다. ".repeat(20), "", "", "hash",
                "NAVER_SEARCH", "PROCESSING", attemptCount, "lease-token");
    }
}

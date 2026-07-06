package com.hana.omnilens.alert.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.hana.omnilens.alert.api.AlertAnalysisPublishRequest;
import com.hana.omnilens.alert.api.AlertCollectPublishRequest;
import com.hana.omnilens.market.application.StockMasterRepository;
import com.hana.omnilens.market.domain.StockSummary;
import com.hana.omnilens.provider.disclosure.OpenDartDisclosureClient;
import com.hana.omnilens.provider.news.NaverNewsArticle;
import com.hana.omnilens.provider.news.NaverNewsClient;
import com.hana.omnilens.provider.news.OriginalArticleClient;
import com.hana.omnilens.provider.news.OriginalArticleContent;

class AlertProviderCollectionServiceTest {

    private final NaverNewsClient naverNewsClient = mock(NaverNewsClient.class);
    private final OriginalArticleClient originalArticleClient = mock(OriginalArticleClient.class);
    private final OpenDartDisclosureClient openDartDisclosureClient = mock(OpenDartDisclosureClient.class);
    private final StockMasterRepository stockMasterRepository = mock(StockMasterRepository.class);
    private final AlertAnalysisPublishingService publishingService = mock(AlertAnalysisPublishingService.class);
    private final AlertDedupeStore dedupeStore = mock(AlertDedupeStore.class);
    private final AlertProviderCollectionService collectionService = new AlertProviderCollectionService(
            naverNewsClient,
            originalArticleClient,
            openDartDisclosureClient,
            stockMasterRepository,
            publishingService,
            dedupeStore,
            Clock.fixed(Instant.parse("2026-07-06T00:00:00Z"), ZoneId.of("Asia/Seoul")));

    @Test
    void skipsCollectedNewsWhenArticleDoesNotMentionRequestedStock() {
        StockSummary stock = new StockSummary(
                "005930",
                "삼성전자",
                "Samsung Electronics",
                "KOSPI",
                "KR7005930003",
                null);
        NaverNewsArticle article = new NaverNewsArticle(
                "하노이에서 생각한 대체불가 대한민국",
                "삼성전자 검색 결과에 노출된 반도체와 AI 산업정책 칼럼입니다.",
                "https://news.example.com/irrelevant-column",
                Instant.parse("2026-07-05T01:00:00Z"));
        OriginalArticleContent fullContent = new OriginalArticleContent(
                "한국은 삼성전자 등 대형 기업이 포함된 반도체, AI, 데이터센터 중심 메가프로젝트를 추진한다.",
                List.of(),
                article.originalUrl(),
                "content-hash",
                "licensed_naver_original_full_text_v1");
        when(stockMasterRepository.findByCode("005930")).thenReturn(Optional.of(stock));
        when(naverNewsClient.search("삼성전자", 1)).thenReturn(List.of(article));
        when(originalArticleClient.fetch(article.originalUrl())).thenReturn(Optional.of(fullContent));

        var response = collectionService.collectAnalyzeAndPublish(new AlertCollectPublishRequest(
                "local-dev",
                List.of("005930"),
                1,
                1));

        assertThat(response.collectedNewsCount()).isEqualTo(1);
        assertThat(response.publishedCount()).isZero();
        assertThat(response.failedAnalysisCount()).isZero();
        verify(publishingService, never()).analyze(org.mockito.ArgumentMatchers.any(AlertAnalysisPublishRequest.class));
    }
}

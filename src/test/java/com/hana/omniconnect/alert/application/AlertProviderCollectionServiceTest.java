package com.hana.omniconnect.alert.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.web.client.ResourceAccessException;

import com.hana.omniconnect.alert.api.AlertAnalysisPublishRequest;
import com.hana.omniconnect.alert.api.AlertCollectPublishRequest;
import com.hana.omniconnect.alert.api.AlertPublishRequest;
import com.hana.omniconnect.alert.domain.AlertEvent;
import com.hana.omniconnect.alert.domain.AlertSummaryLines;
import com.hana.omniconnect.market.application.StockMasterRepository;
import com.hana.omniconnect.market.domain.StockSummary;
import com.hana.omniconnect.provider.disclosure.OpenDartDisclosureClient;
import com.hana.omniconnect.provider.disclosure.OpenDartDisclosure;
import com.hana.omniconnect.provider.news.NaverNewsArticle;
import com.hana.omniconnect.provider.news.NaverNewsClient;
import com.hana.omniconnect.provider.news.OriginalArticleClient;
import com.hana.omniconnect.provider.news.OriginalArticleContent;

class AlertProviderCollectionServiceTest {

    private final NaverNewsClient naverNewsClient = mock(NaverNewsClient.class);
    private final OriginalArticleClient originalArticleClient = mock(OriginalArticleClient.class);
    private final OpenDartDisclosureClient openDartDisclosureClient = mock(OpenDartDisclosureClient.class);
    private final StockMasterRepository stockMasterRepository = mock(StockMasterRepository.class);
    private final AlertAnalysisPublishingService publishingService = mock(AlertAnalysisPublishingService.class);
    private final AlertDedupeStore dedupeStore = mock(AlertDedupeStore.class);
    private final AlertEventRepository alertEventRepository = mock(AlertEventRepository.class);
    private final DisclosureProcessingService disclosureProcessingService = mock(DisclosureProcessingService.class);
    private final NewsProcessingService newsProcessingService = mock(NewsProcessingService.class);
    private final AlertProviderCollectionService collectionService = new AlertProviderCollectionService(
            naverNewsClient,
            originalArticleClient,
            openDartDisclosureClient,
            stockMasterRepository,
            publishingService,
            dedupeStore,
            alertEventRepository,
            disclosureProcessingService,
            newsProcessingService,
            Clock.fixed(Instant.parse("2026-07-06T00:00:00Z"), ZoneId.of("Asia/Seoul")));

    AlertProviderCollectionServiceTest() {
        when(dedupeStore.acquireLease(any(), any())).thenReturn(Optional.of("test-lease-token"));
        when(publishingService.isPublishReady(any(AlertPublishRequest.class))).thenReturn(true);
        when(newsProcessingService.enqueue(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(true);
    }

    @Test
    void collectsOfficialDisclosuresBeforeGeneratedNewsWork() {
        StockSummary stock = new StockSummary(
                "005930", "삼성전자", "Samsung Electronics", "KOSPI", "KR7005930003", "00126380");
        when(stockMasterRepository.findByCode("005930")).thenReturn(Optional.of(stock));
        when(openDartDisclosureClient.search(eq("00126380"), any(), any())).thenReturn(List.of());
        when(naverNewsClient.search(any(), anyInt())).thenReturn(List.of());

        collectionService.collectAnalyzeAndPublish(new AlertCollectPublishRequest(
                "local-dev", List.of("005930"), 5, 1));

        InOrder providerOrder = inOrder(openDartDisclosureClient, naverNewsClient);
        providerOrder.verify(openDartDisclosureClient).search(eq("00126380"), any(), any());
        providerOrder.verify(naverNewsClient, atLeastOnce()).search(any(), anyInt());
    }

    @Test
    void isolatesDisclosureProviderFailureWithoutFailingTheCollectionRequest() {
        StockSummary stock = new StockSummary(
                "005930", "삼성전자", "Samsung Electronics", "KOSPI", "KR7005930003", "00126380");
        when(stockMasterRepository.findByCode("005930")).thenReturn(Optional.of(stock));
        when(naverNewsClient.search(any(), anyInt())).thenReturn(List.of());
        when(openDartDisclosureClient.search(eq("00126380"), any(), any()))
                .thenThrow(new ResourceAccessException("temporary DNS failure"));

        var response = collectionService.collectAnalyzeAndPublish(new AlertCollectPublishRequest(
                "local-dev", List.of("005930"), 5, 1));

        assertThat(response.publishedCount()).isZero();
        assertThat(response.failedAnalysisCount()).isEqualTo(1);
        verify(dedupeStore).releaseLease("COLLECTION_LEASE:local-dev:005930", "test-lease-token");
    }

    @Test
    void fillsOnlyTheMissingLatestSlotFromThePersistentCount() {
        StockSummary stock = new StockSummary(
                "005930", "삼성전자", "Samsung Electronics", "KOSPI", "KR7005930003", null);
        NaverNewsArticle article = new NaverNewsArticle(
                "삼성전자, HBM 수요 회복에 주가 강세",
                "삼성전자 주가와 실적을 다룬 기사입니다.",
                "https://news.example.com/fill-one-slot",
                Instant.parse("2026-07-05T01:00:00Z"));
        when(stockMasterRepository.findByCode("005930")).thenReturn(Optional.of(stock));
        when(alertEventRepository.countByPartnerStockAndSourceType("local-dev", "005930", "NEWS"))
                .thenReturn(4);
        when(naverNewsClient.search(eq("삼성전자 주가"), anyInt())).thenReturn(List.of(article));
        when(originalArticleClient.fetch(article.originalUrl()))
                .thenReturn(Optional.of(stockArticleContent(article)));
        when(dedupeStore.markIfFirst(any())).thenReturn(true);
        when(publishingService.analyzeForCollection(any(AlertAnalysisPublishRequest.class)))
                .thenReturn(publishRequestForStock("005930", "삼성전자", article));

        var response = collectionService.collectAnalyzeAndPublish(new AlertCollectPublishRequest(
                "local-dev", List.of("005930"), 5, 1));

        assertThat(response.publishedCount()).isEqualTo(1);
        verify(naverNewsClient, never()).search(eq("삼성전자 실적"), anyInt());
    }

    @Test
    void doesNotDoubleCountPersistedCandidatesWhileFillingMissingSlots() {
        StockSummary stock = new StockSummary(
                "005930", "삼성전자", "Samsung Electronics", "KOSPI", "KR7005930003", null);
        NaverNewsArticle persisted = new NaverNewsArticle(
                "삼성전자, 기존 HBM 기사",
                "삼성전자 기존 기사입니다.",
                "https://news.example.com/persisted-candidate",
                Instant.parse("2026-07-05T02:00:00Z"));
        NaverNewsArticle fresh = new NaverNewsArticle(
                "삼성전자, 신규 HBM 공급 계약에 주가 강세",
                "삼성전자 신규 공급 계약 기사입니다.",
                "https://news.example.com/fresh-candidate",
                Instant.parse("2026-07-05T03:00:00Z"));
        when(stockMasterRepository.findByCode("005930")).thenReturn(Optional.of(stock));
        when(alertEventRepository.countByPartnerStockAndSourceType("local-dev", "005930", "NEWS"))
                .thenReturn(4);
        when(naverNewsClient.search(eq("삼성전자 주가"), anyInt()))
                .thenReturn(List.of(persisted, fresh));
        AlertEvent persistedEvent = publishReadyEvent();
        when(alertEventRepository.findBySourceIdentity(
                "local-dev", "005930", "NEWS", persisted.originalUrl()))
                .thenReturn(Optional.of(persistedEvent));
        when(originalArticleClient.fetch(fresh.originalUrl()))
                .thenReturn(Optional.of(stockArticleContent(fresh)));
        when(dedupeStore.markIfFirst(any())).thenReturn(true);
        when(publishingService.analyzeForCollection(any(AlertAnalysisPublishRequest.class)))
                .thenReturn(publishRequestForStock("005930", "삼성전자", fresh));

        var response = collectionService.collectAnalyzeAndPublish(new AlertCollectPublishRequest(
                "local-dev", List.of("005930"), 5, 1));

        assertThat(response.publishedCount()).isEqualTo(1);
        verify(originalArticleClient).fetch(fresh.originalUrl());
    }

    @Test
    void checksOneLatestCandidateForAnAlreadySatisfiedStock() {
        StockSummary stock = new StockSummary(
                "005930", "삼성전자", "Samsung Electronics", "KOSPI", "KR7005930003", null);
        NaverNewsArticle article = new NaverNewsArticle(
                "삼성전자, 최신 실적 발표",
                "삼성전자 실적 발표 기사입니다.",
                "https://news.example.com/already-latest",
                Instant.parse("2026-07-05T02:00:00Z"));
        when(stockMasterRepository.findByCode("005930")).thenReturn(Optional.of(stock));
        when(alertEventRepository.countByPartnerStockAndSourceType("local-dev", "005930", "NEWS"))
                .thenReturn(5);
        when(naverNewsClient.search(eq("삼성전자 주가"), anyInt())).thenReturn(List.of(article));
        AlertEvent persistedEvent = publishReadyEvent();
        when(alertEventRepository.findBySourceIdentity(
                "local-dev", "005930", "NEWS", article.originalUrl()))
                .thenReturn(Optional.of(persistedEvent));

        var response = collectionService.collectAnalyzeAndPublish(new AlertCollectPublishRequest(
                "local-dev", List.of("005930"), 5, 1));

        assertThat(response.publishedCount()).isZero();
        verify(originalArticleClient, never()).fetch(article.originalUrl());
        verify(naverNewsClient, never()).search(eq("삼성전자 실적"), anyInt());
    }

    @Test
    void incrementalCollectionPublishesEveryNewCandidateInsideTheWatermarkWindow() {
        StockSummary stock = new StockSummary(
                "005930", "삼성전자", "Samsung Electronics", "KOSPI", "KR7005930003", null);
        NaverNewsArticle first = new NaverNewsArticle(
                "삼성전자, 신규 HBM 공급 계약",
                "삼성전자 주가와 신규 공급 계약 기사입니다.",
                "https://news.example.com/incremental-1",
                Instant.parse("2026-07-05T02:00:00Z"));
        NaverNewsArticle second = new NaverNewsArticle(
                "삼성전자, 반도체 투자 확대",
                "삼성전자 주가와 투자 확대 기사입니다.",
                "https://news.example.com/incremental-2",
                Instant.parse("2026-07-05T03:00:00Z"));
        AlertEvent latest = mock(AlertEvent.class);
        when(latest.publishedAt()).thenReturn(Instant.parse("2026-07-05T01:00:00Z"));
        when(stockMasterRepository.findByCode("005930")).thenReturn(Optional.of(stock));
        when(alertEventRepository.countByPartnerStockAndSourceType("local-dev", "005930", "NEWS"))
                .thenReturn(5);
        when(alertEventRepository.findLatestByPartnerStockAndSourceType(
                "local-dev", "005930", "NEWS"))
                .thenReturn(Optional.of(latest));
        when(naverNewsClient.search(eq("삼성전자"), anyInt())).thenReturn(List.of(first, second));
        when(originalArticleClient.fetch(first.originalUrl()))
                .thenReturn(Optional.of(stockArticleContent(first)));
        when(originalArticleClient.fetch(second.originalUrl()))
                .thenReturn(Optional.of(stockArticleContent(second)));
        when(dedupeStore.markIfFirst(any())).thenReturn(true);
        when(publishingService.analyzeForCollection(any(AlertAnalysisPublishRequest.class)))
                .thenReturn(
                        publishRequestForStock("005930", "삼성전자", first),
                        publishRequestForStock("005930", "삼성전자", second));

        var response = collectionService.collectIncrementalAnalyzeAndPublish(new AlertCollectPublishRequest(
                "local-dev", List.of("005930"), 5, 1));

        assertThat(response.publishedCount()).isZero();
        verify(originalArticleClient).fetch(first.originalUrl());
        verify(originalArticleClient).fetch(second.originalUrl());
        verify(newsProcessingService, times(2))
                .enqueue(any(), eq(stock), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void boundsSlowOriginalArticleInspectionsPerStockAndSearchQuery() {
        StockSummary stock = new StockSummary(
                "015760", "한국전력", "Korea Electric Power", "KOSPI", "KR7015760002", null);
        List<NaverNewsArticle> candidates = IntStream.range(0, 20)
                .mapToObj(index -> new NaverNewsArticle(
                        "한국전력 주가 후보 " + index,
                        "한국전력 주가 관련 기사입니다.",
                        "https://news.example.com/kepco-" + index,
                        Instant.parse("2026-07-05T01:00:00Z").plusSeconds(index)))
                .toList();
        when(stockMasterRepository.findByCode("015760")).thenReturn(Optional.of(stock));
        when(naverNewsClient.search(any(), anyInt())).thenReturn(candidates);
        when(originalArticleClient.fetch(any())).thenReturn(Optional.empty());

        collectionService.collectIncrementalAnalyzeAndPublish(new AlertCollectPublishRequest(
                "local-dev", List.of("015760"), 5, 1));

        verify(originalArticleClient, times(12)).fetch(any());
        verify(naverNewsClient, times(3)).search(any(), anyInt());
    }

    @Test
    void doesNotCountTransientSourceDedupeAsPersisted() {
        StockSummary stock = new StockSummary(
                "005930", "삼성전자", "Samsung Electronics", "KOSPI", "KR7005930003", null);
        NaverNewsArticle article = new NaverNewsArticle(
                "삼성전자, 반도체 수요 회복",
                "삼성전자 주가와 실적을 다룬 기사입니다.",
                "https://news.example.com/in-flight",
                Instant.parse("2026-07-05T03:00:00Z"));
        when(stockMasterRepository.findByCode("005930")).thenReturn(Optional.of(stock));
        when(naverNewsClient.search(eq("삼성전자 주가"), anyInt())).thenReturn(List.of(article));
        when(originalArticleClient.fetch(article.originalUrl()))
                .thenReturn(Optional.of(stockArticleContent(article)));
        when(dedupeStore.markIfFirst(any())).thenReturn(false);

        var response = collectionService.collectAnalyzeAndPublish(new AlertCollectPublishRequest(
                "local-dev", List.of("005930"), 1, 1));

        assertThat(response.publishedCount()).isZero();
        verify(publishingService, never()).analyzeForCollection(any(AlertAnalysisPublishRequest.class));
    }

    @Test
    void reusesPersistedNewsWithoutFetchingOrTranslatingTheArticleAgain() {
        StockSummary stock = new StockSummary(
                "000660",
                "SK하이닉스",
                "SK hynix",
                "KOSPI",
                "KR7000660001",
                null);
        NaverNewsArticle article = new NaverNewsArticle(
                "SK하이닉스, HBM 투자 확대",
                "SK하이닉스 주가와 실적 전망을 다룬 기사입니다.",
                "https://news.example.com/sk-hynix-latest",
                Instant.parse("2026-07-05T01:00:00Z"));
        when(stockMasterRepository.findByCode("000660")).thenReturn(Optional.of(stock));
        when(naverNewsClient.search(eq("SK하이닉스 주가"), anyInt())).thenReturn(List.of(article));
        AlertEvent persistedEvent = publishReadyEvent();
        when(alertEventRepository.findBySourceIdentity(
                "local-dev", "000660", "NEWS", article.originalUrl()))
                .thenReturn(Optional.of(persistedEvent));

        var response = collectionService.collectAnalyzeAndPublish(new AlertCollectPublishRequest(
                "local-dev",
                List.of("000660"),
                1,
                1));

        assertThat(response.publishedCount()).isZero();
        assertThat(response.skippedDuplicateCount()).isEqualTo(1);
        verify(originalArticleClient, never()).fetch(article.originalUrl());
        verify(publishingService, never()).analyzeForCollection(any(AlertAnalysisPublishRequest.class));
    }

    @Test
    void reusesPersistedDisclosureWithoutDownloadingTheOfficialDocumentAgain() {
        StockSummary stock = new StockSummary(
                "000660",
                "SK하이닉스",
                "SK hynix",
                "KOSPI",
                "KR7000660001",
                "00164779");
        OpenDartDisclosure disclosure = new OpenDartDisclosure(
                "20260710000001",
                "SK하이닉스",
                "기업설명회(IR) 개최",
                java.time.LocalDate.of(2026, 7, 10),
                "https://dart.fss.or.kr/dsaf001/main.do?rcpNo=20260710000001");
        when(stockMasterRepository.findByCode("000660")).thenReturn(Optional.of(stock));
        when(openDartDisclosureClient.search(eq("00164779"), any(), any()))
                .thenReturn(List.of(disclosure));
        AlertEvent persistedEvent = publishReadyEvent();
        when(alertEventRepository.findBySourceIdentity(
                "local-dev", "000660", "DISCLOSURE", disclosure.originalUrl()))
                .thenReturn(Optional.of(persistedEvent));

        var response = collectionService.collectAnalyzeAndPublish(new AlertCollectPublishRequest(
                "local-dev",
                List.of("000660"),
                1,
                365));

        assertThat(response.publishedCount()).isZero();
        assertThat(response.skippedDuplicateCount()).isEqualTo(1);
        verify(openDartDisclosureClient, never()).fetchDocumentContent(disclosure.receiptNumber());
        verify(disclosureProcessingService, never()).enqueue(any(), any(), any(), any());
        verify(publishingService, never()).analyzeForCollection(any(AlertAnalysisPublishRequest.class));
    }

    @Test
    void keepsDistinctDisclosureReceiptsWithTheSameReportTitle() {
        StockSummary stock = new StockSummary(
                "000660", "SK하이닉스", "SK hynix", "KOSPI", "KR7000660001", "00164779");
        OpenDartDisclosure older = new OpenDartDisclosure(
                "20260709000001",
                "SK하이닉스",
                "임원ㆍ주요주주 특정증권등 소유상황보고서",
                java.time.LocalDate.of(2026, 7, 9),
                "https://dart.fss.or.kr/dsaf001/main.do?rcpNo=20260709000001");
        OpenDartDisclosure latest = new OpenDartDisclosure(
                "20260710000001",
                "SK하이닉스",
                "임원ㆍ주요주주 특정증권등 소유상황보고서",
                java.time.LocalDate.of(2026, 7, 10),
                "https://dart.fss.or.kr/dsaf001/main.do?rcpNo=20260710000001");
        when(stockMasterRepository.findByCode("000660")).thenReturn(Optional.of(stock));
        when(openDartDisclosureClient.search(eq("00164779"), any(), any()))
                .thenReturn(List.of(older, latest));
        when(disclosureProcessingService.enqueue(any(), any(), any(), any())).thenReturn(true);

        var response = collectionService.collectAnalyzeAndPublish(new AlertCollectPublishRequest(
                "local-dev", List.of("000660"), 2, 365));

        assertThat(response.publishedCount()).isZero();
        verify(disclosureProcessingService, org.mockito.Mockito.times(2))
                .enqueue(eq("local-dev"), eq(stock), any(OpenDartDisclosure.class), any(Instant.class));
        verify(openDartDisclosureClient, never()).fetchDocumentContent(any());
    }

    @Test
    void queuesDisclosureWithoutBlockingOnDocumentDownloadOrQwen() {
        StockSummary stock = new StockSummary(
                "000660",
                "SK하이닉스",
                "SK hynix",
                "KOSPI",
                "KR7000660001",
                "00164779");
        OpenDartDisclosure disclosure = new OpenDartDisclosure(
                "20260710000012",
                "SK하이닉스",
                "투자설명서",
                java.time.LocalDate.of(2026, 7, 10),
                "https://dart.fss.or.kr/dsaf001/main.do?rcpNo=20260710000012");
        when(stockMasterRepository.findByCode("000660")).thenReturn(Optional.of(stock));
        when(openDartDisclosureClient.search(eq("00164779"), any(), any()))
                .thenReturn(List.of(disclosure));
        when(disclosureProcessingService.enqueue(any(), any(), any(), any())).thenReturn(true);

        collectionService.collectAnalyzeAndPublish(new AlertCollectPublishRequest(
                "local-dev",
                List.of("000660"),
                1,
                365));

        verify(disclosureProcessingService).enqueue(
                eq("local-dev"),
                eq(stock),
                eq(disclosure),
                eq(disclosure.receivedAt().atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant()));
        verify(openDartDisclosureClient, never()).fetchDocumentContent(any());
        verify(publishingService, never()).analyzeForCollection(any());
    }

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
                """
                        한국은 삼성전자 등 대형 기업이 포함된 반도체, AI, 데이터센터 중심 메가프로젝트를 추진한다.
                        정부와 산업계는 전력망, 인재, 소재 공급망을 함께 논의했지만 이 글은 특정 상장사의 주가,
                        증시 수급, 공시, 매수·매도 흐름을 다루지 않는 산업정책 칼럼이다.
                        """,
                List.of(),
                article.originalUrl(),
                "content-hash",
                "licensed_naver_original_full_text_v1");
        when(stockMasterRepository.findByCode("005930")).thenReturn(Optional.of(stock));
        when(naverNewsClient.search(eq("삼성전자 주가"), anyInt())).thenReturn(List.of(article));
        when(originalArticleClient.fetch(article.originalUrl())).thenReturn(Optional.of(fullContent));

        var response = collectionService.collectAnalyzeAndPublish(new AlertCollectPublishRequest(
                "local-dev",
                List.of("005930"),
                1,
                1));

        assertThat(response.collectedNewsCount()).isEqualTo(1);
        assertThat(response.publishedCount()).isZero();
        assertThat(response.failedAnalysisCount()).isZero();
        verify(publishingService, never()).analyzeForCollection(org.mockito.ArgumentMatchers.any(AlertAnalysisPublishRequest.class));
    }

    @Test
    void skipsAmbiguousCompanyNameWhenArticleIsSportsOrEntertainment() {
        StockSummary stock = new StockSummary(
                "003550",
                "LG",
                "LG",
                "KOSPI",
                "KR7003550001",
                null);
        NaverNewsArticle article = new NaverNewsArticle(
                "LG 트윈스, 주말 야구 경기 극적 승리",
                "프로야구 LG 트윈스가 홈 경기에서 승리했다.",
                "https://news.example.com/lg-twins",
                Instant.parse("2026-07-05T01:00:00Z"));
        OriginalArticleContent fullContent = new OriginalArticleContent(
                """
                        프로야구 LG 트윈스가 주말 홈 경기에서 극적인 역전승을 거뒀다.
                        관중석을 가득 채운 팬들은 마지막 이닝까지 응원을 이어갔고 선수단은 다음 경기 준비에 들어갔다.
                        이 기사는 구단 경기 결과와 선수 인터뷰를 다루며 상장사 주가나 증시 수급을 설명하지 않는다.
                        """,
                List.of(),
                article.originalUrl(),
                "content-hash",
                "licensed_naver_original_full_text_v1");
        when(stockMasterRepository.findByCode("003550")).thenReturn(Optional.of(stock));
        when(naverNewsClient.search(eq("LG 주가"), anyInt())).thenReturn(List.of(article));
        when(originalArticleClient.fetch(article.originalUrl())).thenReturn(Optional.of(fullContent));

        var response = collectionService.collectAnalyzeAndPublish(new AlertCollectPublishRequest(
                "local-dev",
                List.of("003550"),
                1,
                1));

        assertThat(response.publishedCount()).isZero();
        verify(publishingService, never()).analyzeForCollection(org.mockito.ArgumentMatchers.any(AlertAnalysisPublishRequest.class));
    }

    @Test
    void skipsMultiTopicHotNewsRoundupEvenWhenOneSegmentMentionsStock() {
        StockSummary stock = new StockSummary(
                "005930",
                "삼성전자",
                "Samsung Electronics",
                "KOSPI",
                "KR7005930003",
                null);
        NaverNewsArticle article = new NaverNewsArticle(
                "[이 시각 핫뉴스] 키움증권, 삼성전자에 찬물…목표가 첫 하향 外",
                "연합뉴스TV 기사 중 온라인에서 많이 본 상위권 뉴스입니다.",
                "https://www.yonhapnewstv.co.kr/news/MYH20260710054036KbY",
                Instant.parse("2026-07-09T20:40:37Z"));
        OriginalArticleContent fullContent = new OriginalArticleContent(
                """
                        금요일 아침 이시각 핫뉴스입니다.
                        연합뉴스TV 기사 중 온라인에서 네티즌들이 가장 많이 본 상위권 뉴스는 어떤 것들이 있을까요?
                        키움증권에서 삼성전자의 목표주가를 기존 43만원에서 39만원으로 하향 조정했습니다.
                        다음 소식으로 넘어가보겠습니다. 한 카페에서 주문한 딸기 스무디에서 쇳조각이 발견돼 논란입니다.
                        마지막 기사로 넘어가보시죠. 걸그룹 리센느의 데뷔곡이 음원 차트 정상에 올랐습니다.
                        """,
                List.of(),
                article.originalUrl(),
                "content-hash",
                "licensed_naver_original_full_text_v1");
        when(stockMasterRepository.findByCode("005930")).thenReturn(Optional.of(stock));
        when(naverNewsClient.search(eq("삼성전자 주가"), anyInt())).thenReturn(List.of(article));
        when(originalArticleClient.fetch(article.originalUrl())).thenReturn(Optional.of(fullContent));

        var response = collectionService.collectAnalyzeAndPublish(new AlertCollectPublishRequest(
                "local-dev",
                List.of("005930"),
                1,
                1));

        assertThat(response.collectedNewsCount()).isEqualTo(1);
        assertThat(response.publishedCount()).isZero();
        verify(publishingService, never()).analyzeForCollection(org.mockito.ArgumentMatchers.any(AlertAnalysisPublishRequest.class));
    }

    @Test
    void skipsEmbeddedJsonInsteadOfExtractedArticleBody() {
        StockSummary stock = new StockSummary(
                "126560",
                "현대퓨처넷",
                "Hyundai Futurenet",
                "KOSPI",
                "KR7126560000",
                null);
        NaverNewsArticle article = new NaverNewsArticle(
                "현대퓨처넷, 실적 개선 공시에 강세",
                "현대퓨처넷 주가와 공시를 다룬 기사입니다.",
                "https://news.example.com/embedded-tags",
                Instant.parse("2026-07-09T21:00:00Z"));
        OriginalArticleContent fullContent = new OriginalArticleContent(
                """
                        [{"CODE_TYPE":"T","CODE_ID":"126560","CODE_NM":"현대퓨처넷"},
                        {"CODE_TYPE":"T","CODE_ID":"005930","CODE_NM":"삼성전자"}]
                        현대퓨처넷은 실적 개선과 외국인 순매수로 주가가 상승했다.
                        증권가는 신규 사업과 배당 공시를 투자 변수로 분석했다.
                        """,
                List.of(),
                article.originalUrl(),
                "content-hash",
                "licensed_naver_original_full_text_v1");
        when(stockMasterRepository.findByCode("126560")).thenReturn(Optional.of(stock));
        when(naverNewsClient.search(eq("현대퓨처넷 주가"), anyInt())).thenReturn(List.of(article));
        when(originalArticleClient.fetch(article.originalUrl())).thenReturn(Optional.of(fullContent));

        var response = collectionService.collectAnalyzeAndPublish(new AlertCollectPublishRequest(
                "local-dev",
                List.of("126560"),
                1,
                1));

        assertThat(response.publishedCount()).isZero();
        verify(publishingService, never()).analyzeForCollection(org.mockito.ArgumentMatchers.any(AlertAnalysisPublishRequest.class));
    }

    @Test
    void skipsGenericCorporateDisclosureRoundupWithoutIssuerInTitle() {
        StockSummary stock = new StockSummary(
                "126560",
                "현대퓨처넷",
                "Hyundai Futurenet",
                "KOSPI",
                "KR7126560000",
                null);
        NaverNewsArticle article = new NaverNewsArticle(
                "기업 공시 [4월 3일]",
                "현대퓨처넷과 다른 상장사의 주요 공시를 모았습니다.",
                "https://news.example.com/disclosure-roundup",
                Instant.parse("2026-07-09T21:00:00Z"));
        OriginalArticleContent fullContent = new OriginalArticleContent(
                """
                        이날 기업 공시에는 현대퓨처넷을 포함한 여러 상장사의 공시가 포함됐다.
                        현대퓨처넷은 주요 계약을 공시했고 다른 기업들은 유상증자와 배당을 발표했다.
                        이 기사는 특정 종목의 주가나 실적이 아닌 다수 기업의 공시를 모은 내용이다.
                        """,
                List.of(),
                article.originalUrl(),
                "content-hash",
                "licensed_naver_original_full_text_v1");
        when(stockMasterRepository.findByCode("126560")).thenReturn(Optional.of(stock));
        when(naverNewsClient.search(eq("현대퓨처넷 주가"), anyInt())).thenReturn(List.of(article));
        when(originalArticleClient.fetch(article.originalUrl())).thenReturn(Optional.of(fullContent));

        var response = collectionService.collectAnalyzeAndPublish(new AlertCollectPublishRequest(
                "local-dev",
                List.of("126560"),
                1,
                1));

        assertThat(response.publishedCount()).isZero();
        verify(publishingService, never()).analyzeForCollection(org.mockito.ArgumentMatchers.any(AlertAnalysisPublishRequest.class));
    }

    @Test
    void skipsGeneralInvestmentInterviewWhenStockIsOnlyExampleMention() {
        StockSummary stock = new StockSummary(
                "005930",
                "삼성전자",
                "Samsung Electronics",
                "KOSPI",
                "KR7005930003",
                null);
        NaverNewsArticle article = new NaverNewsArticle(
                "산업 내 1등 하는 좌완 파이어볼러 찾아야 하반기엔 부침 심해질 것",
                "삼성전자, SK하이닉스처럼 메모리 반도체 시장에서 과점적인 지위를 갖고 있어야 한다는 투자 인터뷰입니다.",
                "https://magazine.hankyung.com/money/article/202606197162c",
                Instant.parse("2026-07-09T21:00:00Z"));
        OriginalArticleContent fullContent = new OriginalArticleContent(
                """
                        [커버스토리] 베스트셀러의 통찰 박세익 체슬리투자자문 운용총괄 대표 인터뷰.
                        주식으로 부자됩시다 저자는 기본적 분석과 투자 원칙을 설명했다.
                        엔비디아처럼 독점적인 경쟁력을 갖고 있거나 삼성전자, SK하이닉스처럼
                        메모리 반도체 시장에서 과점적인 지위를 갖고 있어야 한다고 말했다.
                        이 기사는 특정 상장사의 실적, 공시, 목표주가 변경이나 수급을 다루지 않는다.
                        """,
                List.of(),
                article.originalUrl(),
                "content-hash",
                "licensed_naver_original_full_text_v1");
        when(stockMasterRepository.findByCode("005930")).thenReturn(Optional.of(stock));
        when(naverNewsClient.search(eq("삼성전자 주가"), anyInt())).thenReturn(List.of(article));
        when(originalArticleClient.fetch(article.originalUrl())).thenReturn(Optional.of(fullContent));

        var response = collectionService.collectAnalyzeAndPublish(new AlertCollectPublishRequest(
                "local-dev",
                List.of("005930"),
                1,
                1));

        assertThat(response.collectedNewsCount()).isEqualTo(1);
        assertThat(response.publishedCount()).isZero();
        verify(publishingService, never()).analyzeForCollection(org.mockito.ArgumentMatchers.any(AlertAnalysisPublishRequest.class));
    }

    @Test
    void skipsGenericColumnWhenRequestedStockIsNotInTitle() {
        StockSummary stock = new StockSummary(
                "005930",
                "삼성전자",
                "Samsung Electronics",
                "KOSPI",
                "KR7005930003",
                null);
        NaverNewsArticle article = new NaverNewsArticle(
                "[문성진 칼럼] 한국정치, 스케일이 달라져야",
                "삼성전자와 국내 증시가 정책 불확실성에 영향을 받을 수 있다는 칼럼입니다.",
                "https://www.sedaily.com/article/20065888?ref=naver",
                Instant.parse("2026-07-09T21:00:00Z"));
        OriginalArticleContent fullContent = new OriginalArticleContent(
                """
                        이 칼럼은 한국정치와 정책 방향을 다루며 삼성전자와 증시를 예시로 언급한다.
                        특정 상장사의 공시, 실적, 목표주가, 수급, 매수·매도 판단을 직접 보도하지 않는다.
                        투자자는 정치 리스크와 시장 제도를 함께 봐야 한다는 일반 논평이다.
                        """,
                List.of(),
                article.originalUrl(),
                "content-hash",
                "licensed_naver_original_full_text_v1");
        when(stockMasterRepository.findByCode("005930")).thenReturn(Optional.of(stock));
        when(naverNewsClient.search(eq("삼성전자 주가"), anyInt())).thenReturn(List.of(article));
        when(originalArticleClient.fetch(article.originalUrl())).thenReturn(Optional.of(fullContent));

        var response = collectionService.collectAnalyzeAndPublish(new AlertCollectPublishRequest(
                "local-dev",
                List.of("005930"),
                1,
                1));

        assertThat(response.collectedNewsCount()).isEqualTo(1);
        assertThat(response.publishedCount()).isZero();
        verify(publishingService, never()).analyzeForCollection(org.mockito.ArgumentMatchers.any(AlertAnalysisPublishRequest.class));
    }

    @Test
    void skipsArticleFocusedOnAnotherIssuerEvenWhenSnippetMentionsRequestedStock() {
        StockSummary samsungElectronics = new StockSummary(
                "005930",
                "삼성전자",
                "Samsung Electronics",
                "KOSPI",
                "KR7005930003",
                null);
        NaverNewsArticle article = new NaverNewsArticle(
                "SK텔레콤, AI 데이터센터 투자 확대에 강세",
                "삼성전자 검색 결과에 노출됐지만 SK텔레콤 주가와 AI 데이터센터 투자를 다룬 기사입니다.",
                "https://news.example.com/skt-ai-datacenter",
                Instant.parse("2026-07-09T21:00:00Z"));
        OriginalArticleContent fullContent = new OriginalArticleContent(
                """
                        SK텔레콤은 AI 데이터센터 투자 확대 기대와 외국인 순매수에 주가가 강세를 보였다.
                        기사 본문은 SK텔레콤의 실적 전망, 통신 인프라 투자, 증권사 목표주가 변화를 설명한다.
                        삼성전자는 검색어 노출 문맥에서만 언급됐고 이 기사의 투자 판단 대상은 SK텔레콤이다.
                        """,
                List.of(),
                article.originalUrl(),
                "content-hash",
                "licensed_naver_original_full_text_v1");
        when(stockMasterRepository.findByCode("005930")).thenReturn(Optional.of(samsungElectronics));
        when(naverNewsClient.search(eq("삼성전자 주가"), anyInt())).thenReturn(List.of(article));
        when(originalArticleClient.fetch(article.originalUrl())).thenReturn(Optional.of(fullContent));

        var response = collectionService.collectAnalyzeAndPublish(new AlertCollectPublishRequest(
                "local-dev",
                List.of("005930"),
                1,
                1));

        assertThat(response.publishedCount()).isZero();
        verify(publishingService, never()).analyzeForCollection(org.mockito.ArgumentMatchers.any(AlertAnalysisPublishRequest.class));
    }

    @Test
    void skipsMultiIssuerNicknameArticleForSingleRequestedStock() {
        StockSummary samsungElectronics = new StockSummary(
                "005930",
                "삼성전자",
                "Samsung Electronics",
                "KOSPI",
                "KR7005930003",
                null);
        NaverNewsArticle article = new NaverNewsArticle(
                "美ADR 상장 기대감…삼전닉스 장 초반 반등",
                "삼성전자와 SK하이닉스를 묶은 반도체 대형주 주가 기사입니다.",
                "https://news.example.com/samjeon-nix",
                Instant.parse("2026-07-09T21:00:00Z"));
        OriginalArticleContent fullContent = new OriginalArticleContent(
                """
                        삼성전자와 SK하이닉스가 미국 ADR 상장 기대감과 반도체 업황 회복 기대에 함께 반등했다.
                        증권가는 두 회사의 HBM 공급 확대와 메모리 가격 반등을 공통 주가 변수로 제시했다.
                        이 기사는 특정 한 종목의 공시나 실적이 아니라 반도체 대형주 묶음 흐름을 설명한다.
                        """,
                List.of(),
                article.originalUrl(),
                "content-hash",
                "licensed_naver_original_full_text_v1");
        when(stockMasterRepository.findByCode("005930")).thenReturn(Optional.of(samsungElectronics));
        when(naverNewsClient.search(eq("삼성전자 주가"), anyInt())).thenReturn(List.of(article));
        when(originalArticleClient.fetch(article.originalUrl())).thenReturn(Optional.of(fullContent));

        var response = collectionService.collectAnalyzeAndPublish(new AlertCollectPublishRequest(
                "local-dev",
                List.of("005930"),
                1,
                1));

        assertThat(response.publishedCount()).isZero();
        verify(publishingService, never()).analyzeForCollection(org.mockito.ArgumentMatchers.any(AlertAnalysisPublishRequest.class));
    }

    @Test
    void skipsArticleWhenDifferentListedIssuerAppearsBeforeRequestedStockInTitle() {
        StockSummary samsungElectronics = new StockSummary(
                "005930",
                "삼성전자",
                "Samsung Electronics",
                "KOSPI",
                "KR7005930003",
                null);
        StockSummary skHynix = new StockSummary(
                "000660",
                "SK하이닉스",
                "SK hynix",
                "KOSPI",
                "KR7000660001",
                null);
        NaverNewsArticle article = new NaverNewsArticle(
                "300조 눈앞… SK하이닉스, 삼성전자 뒤이어 사상 최대 예고",
                "SK하이닉스 시가총액과 주가 흐름을 삼성전자와 비교한 기사입니다.",
                "https://news.example.com/hynix-market-cap",
                Instant.parse("2026-07-09T21:00:00Z"));
        OriginalArticleContent fullContent = new OriginalArticleContent(
                """
                        SK하이닉스가 HBM 수요와 외국인 순매수에 힘입어 시가총액 300조원에 근접했다.
                        시장에서는 삼성전자 뒤를 잇는 사상 최대 시총 가능성을 거론하며 SK하이닉스 목표주가를 재평가했다.
                        이 기사는 삼성전자 자체 공시나 실적이 아니라 SK하이닉스의 주가 흐름과 수급을 중심으로 설명한다.
                        """,
                List.of(),
                article.originalUrl(),
                "content-hash",
                "licensed_naver_original_full_text_v1");
        when(stockMasterRepository.findByCode("005930")).thenReturn(Optional.of(samsungElectronics));
        when(stockMasterRepository.search("sk하이닉스")).thenReturn(List.of(skHynix));
        when(naverNewsClient.search(eq("삼성전자 주가"), anyInt())).thenReturn(List.of(article));
        when(originalArticleClient.fetch(article.originalUrl())).thenReturn(Optional.of(fullContent));

        var response = collectionService.collectAnalyzeAndPublish(new AlertCollectPublishRequest(
                "local-dev",
                List.of("005930"),
                1,
                1));

        assertThat(response.publishedCount()).isZero();
        verify(publishingService, never()).analyzeForCollection(org.mockito.ArgumentMatchers.any(AlertAnalysisPublishRequest.class));
    }

    @Test
    void skipsArticleWhenOriginalPageTitleShowsDifferentFocusThanSearchTitle() {
        StockSummary samsungElectronics = new StockSummary(
                "005930",
                "삼성전자",
                "Samsung Electronics",
                "KOSPI",
                "KR7005930003",
                null);
        NaverNewsArticle article = new NaverNewsArticle(
                "삼성전자 검색 결과: 코스피 AI 반도체 훈풍",
                "삼성전자 검색 결과에 노출됐지만 실제 원문은 SK하이닉스 중심 기사입니다.",
                "https://news.example.com/search-title-mismatch",
                Instant.parse("2026-07-09T21:00:00Z"));
        OriginalArticleContent fullContent = new OriginalArticleContent(
                """
                        코스피가 AI 반도체 훈풍에 급등 출발했고 SK하이닉스가 3%대 강세를 보였다.
                        SK하이닉스 ADR 상장과 HBM 수요가 투자심리를 끌어올렸다는 분석이 나왔다.
                        삼성전자는 검색 결과 문맥에만 포함됐고 원문 제목과 본문 중심은 SK하이닉스다.
                        """,
                List.of(),
                article.originalUrl(),
                "content-hash",
                "licensed_naver_original_full_text_v1",
                "코스피, AI 반도체 훈풍에 3%대 급등 출발…SK하이닉스 3%대 강세");
        when(stockMasterRepository.findByCode("005930")).thenReturn(Optional.of(samsungElectronics));
        when(naverNewsClient.search(eq("삼성전자 주가"), anyInt())).thenReturn(List.of(article));
        when(originalArticleClient.fetch(article.originalUrl())).thenReturn(Optional.of(fullContent));

        var response = collectionService.collectAnalyzeAndPublish(new AlertCollectPublishRequest(
                "local-dev",
                List.of("005930"),
                1,
                1));

        assertThat(response.publishedCount()).isZero();
        verify(publishingService, never()).analyzeForCollection(org.mockito.ArgumentMatchers.any(AlertAnalysisPublishRequest.class));
    }

    @Test
    void skipsMarketArticleWhenSearchTitleMentionsRequestedStockButPageTitleDoesNot() {
        StockSummary samsungElectronics = new StockSummary(
                "005930",
                "삼성전자",
                "Samsung Electronics",
                "KOSPI",
                "KR7005930003",
                null);
        NaverNewsArticle article = new NaverNewsArticle(
                "삼성전자 검색 결과: 이틀 연속 미국 반도체 랠리에 코스피도 강세",
                "삼성전자가 검색 설명에 포함된 최신 증시 기사입니다.",
                "https://news.example.com/market-wide-semiconductor-rally",
                Instant.parse("2026-07-09T21:00:00Z"));
        OriginalArticleContent fullContent = new OriginalArticleContent(
                """
                        이틀 연속 미국 반도체 랠리에 코스피가 장 초반 강세를 보였다.
                        외국인 순매수와 기관 매수세가 유입되며 지수 전반이 상승했다.
                        삼성전자와 SK하이닉스도 반도체 투자심리 개선의 영향을 받았지만 이 기사는 증시 전반을 설명한다.
                        """,
                List.of(),
                article.originalUrl(),
                "content-hash",
                "licensed_naver_original_full_text_v1",
                "이틀 연속 美 반도체 랠리에 코스피도 강세");
        when(stockMasterRepository.findByCode("005930")).thenReturn(Optional.of(samsungElectronics));
        when(naverNewsClient.search(eq("삼성전자 주가"), anyInt())).thenReturn(List.of(article));
        when(originalArticleClient.fetch(article.originalUrl())).thenReturn(Optional.of(fullContent));

        var response = collectionService.collectAnalyzeAndPublish(new AlertCollectPublishRequest(
                "local-dev",
                List.of("005930"),
                1,
                1));

        assertThat(response.publishedCount()).isZero();
        verify(publishingService, never()).analyzeForCollection(org.mockito.ArgumentMatchers.any(AlertAnalysisPublishRequest.class));
    }

    @Test
    void publishesArticleWhenRequestedIssuerLeadsStockNewsTitle() {
        StockSummary samsungElectronics = new StockSummary(
                "005930",
                "삼성전자",
                "Samsung Electronics",
                "KOSPI",
                "KR7005930003",
                null);
        NaverNewsArticle article = new NaverNewsArticle(
                "삼성전자, HBM 수요 회복에 주가 강세",
                "삼성전자 주가와 외국인 순매수를 다룬 증권 기사입니다.",
                "https://news.example.com/samsung-hbm",
                Instant.parse("2026-07-09T21:00:00Z"));
        OriginalArticleContent fullContent = new OriginalArticleContent(
                """
                        삼성전자는 HBM 수요 회복 기대와 외국인 순매수 유입에 주가가 강세를 보였다.
                        증권가는 메모리 가격 반등과 실적 개선 가능성을 주가 상승 배경으로 꼽았다.
                        투자자는 분기 실적과 반도체 업황 회복 속도를 함께 확인해야 한다.
                        장중 거래대금도 반도체 대형주에 집중됐고 기관 투자자 매수세가 더해졌다.
                        시장에서는 HBM 공급 계약과 하반기 설비투자 계획이 추가 변수로 거론됐다.
                        """,
                List.of(),
                article.originalUrl(),
                "content-hash",
                "licensed_naver_original_full_text_v1");
        when(stockMasterRepository.findByCode("005930")).thenReturn(Optional.of(samsungElectronics));
        when(naverNewsClient.search(eq("삼성전자 주가"), anyInt())).thenReturn(List.of(article));
        when(originalArticleClient.fetch(article.originalUrl())).thenReturn(Optional.of(fullContent));
        when(dedupeStore.markIfFirst(any())).thenReturn(true);
        when(publishingService.analyzeForCollection(any(AlertAnalysisPublishRequest.class)))
                .thenReturn(publishRequestForStock("005930", "삼성전자", article));

        var response = collectionService.collectAnalyzeAndPublish(new AlertCollectPublishRequest(
                "local-dev",
                List.of("005930"),
                1,
                1));

        assertThat(response.publishedCount()).isEqualTo(1);
        verify(publishingService).publishAnalyzed(any(AlertPublishRequest.class));
    }

    @Test
    void reusesPersistedAiDuplicateAndReleasesUnstoredSourceIdentity() {
        StockSummary stock = new StockSummary(
                "005930", "삼성전자", "Samsung Electronics", "KOSPI", "KR7005930003", null);
        NaverNewsArticle article = new NaverNewsArticle(
                "삼성전자, HBM 실적 개선에 주가 강세",
                "삼성전자 주가와 실적을 다룬 기사입니다.",
                "https://news.example.com/ai-duplicate",
                Instant.parse("2026-07-09T21:00:00Z"));
        OriginalArticleContent fullContent = stockArticleContent(article);
        when(stockMasterRepository.findByCode("005930")).thenReturn(Optional.of(stock));
        when(naverNewsClient.search(eq("삼성전자 주가"), anyInt())).thenReturn(List.of(article));
        when(originalArticleClient.fetch(article.originalUrl())).thenReturn(Optional.of(fullContent));
        when(dedupeStore.markIfFirst(any())).thenReturn(true);
        when(publishingService.analyzeForCollection(any(AlertAnalysisPublishRequest.class)))
                .thenReturn(publishRequestForStock("005930", "삼성전자", article));
        when(alertEventRepository.findByDuplicateIdentity(
                "local-dev", "005930", "NEWS", "duplicate-key"))
                .thenReturn(Optional.of(mock(AlertEvent.class)));

        var response = collectionService.collectAnalyzeAndPublish(new AlertCollectPublishRequest(
                "local-dev", List.of("005930"), 1, 1));

        assertThat(response.publishedCount()).isZero();
        verify(dedupeStore).remove("local-dev:COLLECTION:v2:005930:NEWS:" + article.originalUrl());
    }

    @Test
    void releasesBothDedupeKeysWhenPublicationFailsAfterAnalysis() {
        StockSummary stock = new StockSummary(
                "005930", "삼성전자", "Samsung Electronics", "KOSPI", "KR7005930003", null);
        NaverNewsArticle article = new NaverNewsArticle(
                "삼성전자, HBM 실적 개선에 주가 강세",
                "삼성전자 주가와 실적을 다룬 기사입니다.",
                "https://news.example.com/publish-failure",
                Instant.parse("2026-07-09T21:00:00Z"));
        OriginalArticleContent fullContent = stockArticleContent(article);
        when(stockMasterRepository.findByCode("005930")).thenReturn(Optional.of(stock));
        when(naverNewsClient.search(eq("삼성전자 주가"), anyInt())).thenReturn(List.of(article));
        when(originalArticleClient.fetch(article.originalUrl())).thenReturn(Optional.of(fullContent));
        when(dedupeStore.markIfFirst(any())).thenReturn(true);
        when(publishingService.analyzeForCollection(any(AlertAnalysisPublishRequest.class)))
                .thenReturn(publishRequestForStock("005930", "삼성전자", article));
        when(publishingService.publishAnalyzed(any(AlertPublishRequest.class)))
                .thenThrow(new IllegalStateException("database unavailable"));

        var response = collectionService.collectAnalyzeAndPublish(new AlertCollectPublishRequest(
                "local-dev", List.of("005930"), 1, 1));

        assertThat(response.publishedCount()).isZero();
        verify(dedupeStore).remove("local-dev:COLLECTION:v2:005930:NEWS:" + article.originalUrl());
        verify(dedupeStore).remove("local-dev:AI:v2:005930:NEWS:duplicate-key");
    }

    @Test
    void skipsArticleWhenAnotherListedIssuerLeadsTitleAndRequestedStockIsBackground() {
        StockSummary samsungElectronics = new StockSummary(
                "005930",
                "삼성전자",
                "Samsung Electronics",
                "KOSPI",
                "KR7005930003",
                null);
        StockSummary samsungCt = new StockSummary(
                "028260",
                "삼성물산",
                "Samsung C&T",
                "KOSPI",
                "KR7028260008",
                null);
        NaverNewsArticle article = new NaverNewsArticle(
                "삼성물산, 삼성전자 FCF 증가에 주주환원 확대 전망",
                "삼성물산이 삼성전자 배당수익을 바탕으로 주주환원을 늘릴 수 있다는 증권가 분석입니다.",
                "https://news.example.com/samsung-ct",
                Instant.parse("2026-07-09T21:00:00Z"));
        OriginalArticleContent fullContent = new OriginalArticleContent(
                """
                        삼성물산은 삼성전자 잉여현금흐름 개선에 따른 관계사 배당수익 증가로 주주환원 확대가 가능하다는 분석이 나왔다.
                        증권가는 삼성물산의 지분 가치와 배당 정책 변화 가능성을 중심으로 목표주가를 조정했다.
                        이 기사는 삼성전자 자체 실적, 주가, 공시가 아니라 삼성물산 투자 포인트를 다룬다.
                        """,
                List.of(),
                article.originalUrl(),
                "content-hash",
                "licensed_naver_original_full_text_v1");
        when(stockMasterRepository.findByCode("005930")).thenReturn(Optional.of(samsungElectronics));
        when(stockMasterRepository.search("삼성물산")).thenReturn(List.of(samsungCt));
        when(naverNewsClient.search(eq("삼성전자 주가"), anyInt())).thenReturn(List.of(article));
        when(originalArticleClient.fetch(article.originalUrl())).thenReturn(Optional.of(fullContent));

        var response = collectionService.collectAnalyzeAndPublish(new AlertCollectPublishRequest(
                "local-dev",
                List.of("005930"),
                1,
                1));

        assertThat(response.collectedNewsCount()).isEqualTo(1);
        assertThat(response.publishedCount()).isZero();
        verify(publishingService, never()).analyzeForCollection(org.mockito.ArgumentMatchers.any(AlertAnalysisPublishRequest.class));
    }

    @Test
    void skipsCollectedNewsWhenAnalysisMatchesDifferentStock() {
        StockSummary samsungElectronics = new StockSummary(
                "005930",
                "삼성전자",
                "Samsung Electronics",
                "KOSPI",
                "KR7005930003",
                null);
        NaverNewsArticle article = new NaverNewsArticle(
                "삼성전자·SK하이닉스 동반 강세",
                "반도체 대형주 주가와 외국인 수급을 다룬 기사입니다.",
                "https://news.example.com/chip-rally",
                Instant.parse("2026-07-09T21:00:00Z"));
        OriginalArticleContent fullContent = new OriginalArticleContent(
                """
                        삼성전자와 SK하이닉스가 반도체 업황 개선 기대에 동반 강세를 보였다.
                        증권가는 외국인 순매수와 메모리 가격 반등이 주가 상승을 이끌었다고 분석했다.
                        투자자는 두 회사의 실적 전망과 수급 변화를 함께 확인해야 한다.
                        장중 거래대금도 대형 반도체주에 집중됐고 기관 투자자 매수세가 더해지며 지수 영향력이 커졌다.
                        시장에서는 HBM 공급 계약, 메모리 가격 협상, 하반기 설비투자 계획이 추가 주가 변수로 거론됐다.
                        """,
                List.of(),
                article.originalUrl(),
                "content-hash",
                "licensed_naver_original_full_text_v1");
        when(stockMasterRepository.findByCode("005930")).thenReturn(Optional.of(samsungElectronics));
        when(naverNewsClient.search(eq("삼성전자 주가"), anyInt())).thenReturn(List.of(article));
        when(originalArticleClient.fetch(article.originalUrl())).thenReturn(Optional.of(fullContent));
        when(dedupeStore.markIfFirst(any())).thenReturn(true);
        when(publishingService.analyzeForCollection(any(AlertAnalysisPublishRequest.class)))
                .thenReturn(publishRequestForStock("000660", "SK하이닉스", article));

        var response = collectionService.collectAnalyzeAndPublish(new AlertCollectPublishRequest(
                "local-dev",
                List.of("005930"),
                1,
                1));

        assertThat(response.publishedCount()).isZero();
        assertThat(response.failedAnalysisCount()).isGreaterThanOrEqualTo(1);
        verify(publishingService, never()).publishAnalyzed(any(AlertPublishRequest.class));
    }

    private AlertPublishRequest publishRequestForStock(
            String stockCode,
            String stockName,
            NaverNewsArticle article) {
        return new AlertPublishRequest(
                "local-dev",
                stockCode,
                stockName,
                "NEWS",
                article.title(),
                "Samsung Electronics and SK Hynix rose together.",
                "반도체 대형주 동반 강세",
                new AlertSummaryLines(
                        "Chip bellwethers rose on memory-cycle optimism.",
                        "Foreign buying and memory-price recovery drove sentiment.",
                        "Investors should track earnings and flows."),
                "Chip bellwethers rose on memory-cycle optimism.",
                "삼성전자와 SK하이닉스가 반도체 업황 개선 기대에 동반 강세를 보였다.",
                "Samsung Electronics and SK Hynix rose together on memory-cycle optimism.",
                List.of(),
                "FULL_TEXT",
                article.originalUrl(),
                article.publishedAt(),
                List.of("STOCK_NEWS"),
                "POSITIVE",
                "MEDIUM",
                null,
                null,
                null,
                List.of(stockCode),
                false,
                false,
                List.of(),
                List.of(),
                "local-open-source-qwen",
                "Qwen3-4B-GGUF-Q4",
                "TRANSLATED",
                "duplicate-key",
                "cluster-key",
                "test-model",
                0.9,
                0.9,
                0.9,
                0.9);
    }

    private OriginalArticleContent stockArticleContent(NaverNewsArticle article) {
        return new OriginalArticleContent(
                """
                        삼성전자는 HBM 수요 회복과 외국인 순매수에 주가가 강세를 보였다.
                        증권가는 메모리 가격 반등과 영업이익 개선을 주요 상승 배경으로 꼽았다.
                        투자자는 분기 실적과 반도체 설비투자 계획을 함께 확인해야 한다.
                        HBM 신제품 공급 계약과 고객사 품질 승인 일정도 하반기 실적의 핵심 변수로 거론됐다.
                        장중 거래대금은 반도체 대형주에 집중됐고 기관 투자자의 매수세도 유입됐다.
                        """,
                List.of(),
                article.originalUrl(),
                "content-hash",
                "licensed_naver_original_full_text_v1");
    }

    private AlertEvent publishReadyEvent() {
        AlertEvent event = mock(AlertEvent.class);
        when(event.originalContent()).thenReturn(
                "삼성전자는 반도체 수요 회복을 공시했다. 투자자는 실적과 수급 변화를 확인해야 한다.");
        when(event.summaryLines()).thenReturn(new AlertSummaryLines(
                "Samsung Electronics reported a semiconductor-demand recovery.",
                "The source cited improving memory demand.",
                "Investors should monitor earnings and trading flows."));
        when(event.translatedSummary()).thenReturn(
                "Samsung Electronics reported a semiconductor-demand recovery.");
        when(event.translatedContent()).thenReturn(
                "Samsung Electronics reported recovering semiconductor demand. "
                        + "The filing explains the expected earnings effect. "
                        + "Investors should monitor trading flows.");
        return event;
    }
}

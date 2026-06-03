package com.hana.omnilens.alert.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.hana.omnilens.alert.api.AlertAnalysisPublishRequest;
import com.hana.omnilens.alert.api.AlertCollectPublishRequest;
import com.hana.omnilens.alert.api.AlertCollectPublishResponse;
import com.hana.omnilens.alert.domain.AlertEvent;
import com.hana.omnilens.market.application.StockMasterRepository;
import com.hana.omnilens.market.domain.StockSummary;
import com.hana.omnilens.provider.disclosure.OpenDartDisclosure;
import com.hana.omnilens.provider.disclosure.OpenDartDisclosureClient;
import com.hana.omnilens.provider.news.NaverNewsArticle;
import com.hana.omnilens.provider.news.NaverNewsClient;

@Service
public class AlertProviderCollectionService {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    private final NaverNewsClient naverNewsClient;
    private final OpenDartDisclosureClient openDartDisclosureClient;
    private final StockMasterRepository stockMasterRepository;
    private final AlertAnalysisPublishingService alertAnalysisPublishingService;
    private final BoundedDedupeStore publishedSourceKeys = new BoundedDedupeStore(10_000);
    private final Clock clock;

    @Autowired
    public AlertProviderCollectionService(
            NaverNewsClient naverNewsClient,
            OpenDartDisclosureClient openDartDisclosureClient,
            StockMasterRepository stockMasterRepository,
            AlertAnalysisPublishingService alertAnalysisPublishingService) {
        this(
                naverNewsClient,
                openDartDisclosureClient,
                stockMasterRepository,
                alertAnalysisPublishingService,
                Clock.system(KOREA_ZONE));
    }

    AlertProviderCollectionService(
            NaverNewsClient naverNewsClient,
            OpenDartDisclosureClient openDartDisclosureClient,
            StockMasterRepository stockMasterRepository,
            AlertAnalysisPublishingService alertAnalysisPublishingService,
            Clock clock) {
        this.naverNewsClient = naverNewsClient;
        this.openDartDisclosureClient = openDartDisclosureClient;
        this.stockMasterRepository = stockMasterRepository;
        this.alertAnalysisPublishingService = alertAnalysisPublishingService;
        this.clock = clock;
    }

    public AlertCollectPublishResponse collectAnalyzeAndPublish(AlertCollectPublishRequest request) {
        List<StockSummary> stocks = uniqueStocks(request.stockCodes());
        if (stocks.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "no supported stock codes");
        }

        CollectionCounters counters = new CollectionCounters();
        List<AlertEvent> events = new ArrayList<>();
        LocalDate endDate = LocalDate.now(clock);
        LocalDate beginDate = endDate.minusDays(request.effectiveDisclosureLookbackDays());

        for (StockSummary stock : stocks) {
            publishNews(request, stock, counters, events);
            publishDisclosures(request, stock, beginDate, endDate, counters, events);
        }

        return new AlertCollectPublishResponse(
                request.partnerId(),
                stocks.stream().map(StockSummary::stockCode).toList(),
                counters.collectedNewsCount,
                counters.collectedDisclosureCount,
                events.size(),
                counters.skippedDuplicateCount,
                counters.failedAnalysisCount,
                events);
    }

    private List<StockSummary> uniqueStocks(List<String> stockCodes) {
        Set<String> uniqueCodes = new LinkedHashSet<>(stockCodes);
        return uniqueCodes.stream()
                .map(stockMasterRepository::findByCode)
                .flatMap(optional -> optional.stream())
                .toList();
    }

    private void publishNews(
            AlertCollectPublishRequest request,
            StockSummary stock,
            CollectionCounters counters,
            List<AlertEvent> events) {
        List<NaverNewsArticle> articles = naverNewsClient.search(stock.stockName(), request.effectiveNewsDisplay());
        counters.collectedNewsCount += articles.size();
        for (NaverNewsArticle article : articles) {
            publishCollectedAlert(
                    request.partnerId(),
                    stock,
                    "NEWS",
                    article.title(),
                    article.snippet(),
                    article.originalUrl(),
                    article.publishedAt(),
                    counters,
                    events);
        }
    }

    private void publishDisclosures(
            AlertCollectPublishRequest request,
            StockSummary stock,
            LocalDate beginDate,
            LocalDate endDate,
            CollectionCounters counters,
            List<AlertEvent> events) {
        if (stock.dartCorpCode() == null || stock.dartCorpCode().isBlank()) {
            return;
        }

        List<OpenDartDisclosure> disclosures = openDartDisclosureClient.search(
                stock.dartCorpCode(),
                beginDate,
                endDate);
        counters.collectedDisclosureCount += disclosures.size();
        for (OpenDartDisclosure disclosure : disclosures) {
            publishCollectedAlert(
                    request.partnerId(),
                    stock,
                    "DISCLOSURE",
                    disclosure.corporationName() + " " + disclosure.reportName(),
                    disclosure.reportName(),
                    disclosure.originalUrl(),
                    disclosure.receivedAt().atStartOfDay(KOREA_ZONE).toInstant(),
                    counters,
                    events);
        }
    }

    private void publishCollectedAlert(
            String partnerId,
            StockSummary stock,
            String sourceType,
            String title,
            String snippet,
            String originalUrl,
            Instant publishedAt,
            CollectionCounters counters,
            List<AlertEvent> events) {
        String sourceKey = partnerId + ":" + sourceType + ":" + originalUrl;
        if (!publishedSourceKeys.markIfFirst(sourceKey)) {
            counters.skippedDuplicateCount++;
            return;
        }

        try {
            events.add(alertAnalysisPublishingService.analyzeAndPublish(new AlertAnalysisPublishRequest(
                    partnerId,
                    sourceType,
                    title,
                    snippet,
                    originalUrl,
                    publishedAt,
                    List.of(new AlertAnalysisPublishRequest.StockCandidateRequest(
                            stock.stockCode(),
                            stock.stockName(),
                            stock.stockNameEn(),
                            List.of(stock.stockNameEn()))))));
        } catch (ResponseStatusException exception) {
            publishedSourceKeys.remove(sourceKey);
            counters.failedAnalysisCount++;
        }
    }

    private static class CollectionCounters {
        private int collectedNewsCount;
        private int collectedDisclosureCount;
        private int skippedDuplicateCount;
        private int failedAnalysisCount;
    }

    private static class BoundedDedupeStore {

        private final int maxEntries;
        private final Map<String, Boolean> keys;

        BoundedDedupeStore(int maxEntries) {
            this.maxEntries = maxEntries;
            this.keys = new LinkedHashMap<>() {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                    return size() > BoundedDedupeStore.this.maxEntries;
                }
            };
        }

        synchronized boolean markIfFirst(String key) {
            // DB/Redis dedupe가 붙기 전까지 프로세스 단위 중복 재발행을 제한한다.
            if (keys.containsKey(key)) {
                return false;
            }
            keys.put(key, true);
            return true;
        }

        synchronized void remove(String key) {
            keys.remove(key);
        }
    }
}

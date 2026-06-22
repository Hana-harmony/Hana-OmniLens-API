package com.hana.omnilens.alert.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.hana.omnilens.alert.api.AlertAnalysisPublishRequest;
import com.hana.omnilens.alert.api.AlertCollectPublishRequest;
import com.hana.omnilens.alert.api.AlertCollectPublishResponse;
import com.hana.omnilens.alert.api.AlertPublishRequest;
import com.hana.omnilens.alert.domain.AlertEvent;
import com.hana.omnilens.market.application.StockMasterRepository;
import com.hana.omnilens.market.domain.StockSummary;
import com.hana.omnilens.provider.disclosure.OpenDartDisclosure;
import com.hana.omnilens.provider.disclosure.OpenDartDisclosureClient;
import com.hana.omnilens.provider.disclosure.OpenDartDisclosureDocument;
import com.hana.omnilens.provider.news.NaverNewsArticle;
import com.hana.omnilens.provider.news.NaverNewsClient;
import com.hana.omnilens.provider.news.OriginalArticleClient;
import com.hana.omnilens.provider.news.OriginalArticleContent;

@Service
public class AlertProviderCollectionService {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    private final NaverNewsClient naverNewsClient;
    private final OriginalArticleClient originalArticleClient;
    private final OpenDartDisclosureClient openDartDisclosureClient;
    private final StockMasterRepository stockMasterRepository;
    private final AlertAnalysisPublishingService alertAnalysisPublishingService;
    private final AlertDedupeStore alertDedupeStore;
    private final Clock clock;

    @Autowired
    public AlertProviderCollectionService(
            NaverNewsClient naverNewsClient,
            OriginalArticleClient originalArticleClient,
            OpenDartDisclosureClient openDartDisclosureClient,
            StockMasterRepository stockMasterRepository,
            AlertAnalysisPublishingService alertAnalysisPublishingService,
            AlertDedupeStore alertDedupeStore) {
        this(
                naverNewsClient,
                originalArticleClient,
                openDartDisclosureClient,
                stockMasterRepository,
                alertAnalysisPublishingService,
                alertDedupeStore,
                Clock.system(KOREA_ZONE));
    }

    AlertProviderCollectionService(
            NaverNewsClient naverNewsClient,
            OriginalArticleClient originalArticleClient,
            OpenDartDisclosureClient openDartDisclosureClient,
            StockMasterRepository stockMasterRepository,
            AlertAnalysisPublishingService alertAnalysisPublishingService,
            AlertDedupeStore alertDedupeStore,
            Clock clock) {
        this.naverNewsClient = naverNewsClient;
        this.originalArticleClient = originalArticleClient;
        this.openDartDisclosureClient = openDartDisclosureClient;
        this.stockMasterRepository = stockMasterRepository;
        this.alertAnalysisPublishingService = alertAnalysisPublishingService;
        this.alertDedupeStore = alertDedupeStore;
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
            CollectedContent fullContent = originalArticleClient.fetch(article.originalUrl())
                    .map(CollectedContent::fromArticle)
                    .orElseGet(() -> CollectedContent.discoveryOnly(article.originalUrl()));
            publishCollectedAlert(
                    request.partnerId(),
                    stock,
                    "NEWS",
                    article.title(),
                    article.snippet(),
                    fullContent,
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
            CollectedContent fullContent = optionalDocument(disclosure.receiptNumber())
                    .map(CollectedContent::fromDisclosure)
                    .orElseGet(() -> CollectedContent.officialDisclosure(disclosure.originalUrl()));
            publishCollectedAlert(
                    request.partnerId(),
                    stock,
                    "DISCLOSURE",
                    disclosure.corporationName() + " " + disclosure.reportName(),
                    disclosure.reportName(),
                    fullContent,
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
            CollectedContent fullContent,
            String originalUrl,
            Instant publishedAt,
            CollectionCounters counters,
            List<AlertEvent> events) {
        String sourceKey = partnerId + ":" + sourceType + ":" + originalUrl;
        if (!alertDedupeStore.markIfFirst(sourceKey)) {
            counters.skippedDuplicateCount++;
            return;
        }

        try {
            AlertPublishRequest analyzedAlert = alertAnalysisPublishingService.analyze(new AlertAnalysisPublishRequest(
                    partnerId,
                    sourceType,
                    title,
                    snippet,
                    fullContent.content(),
                    fullContent.imageUrls(),
                    fullContent.canonicalUrl(),
                    fullContent.contentHash(),
                    fullContent.sourceLicensePolicy(),
                    originalUrl,
                    publishedAt,
                    List.of(new AlertAnalysisPublishRequest.StockCandidateRequest(
                            stock.stockCode(),
                            stock.stockName(),
                            stock.stockNameEn(),
                            List.of(stock.stockNameEn())))));
            String aiDuplicateKey = aiDuplicateKey(partnerId, analyzedAlert);
            if (aiDuplicateKey != null && !alertDedupeStore.markIfFirst(aiDuplicateKey)) {
                counters.skippedDuplicateCount++;
                return;
            }
            events.add(alertAnalysisPublishingService.publishAnalyzed(analyzedAlert));
        } catch (ResponseStatusException exception) {
            alertDedupeStore.remove(sourceKey);
            counters.failedAnalysisCount++;
        }
    }

    private static String aiDuplicateKey(String partnerId, AlertPublishRequest request) {
        if (!StringUtils.hasText(request.duplicateKey())) {
            return null;
        }
        return partnerId + ":AI:" + request.sourceType() + ":" + request.duplicateKey();
    }

    private Optional<OpenDartDisclosureDocument> optionalDocument(String receiptNumber) {
        Optional<OpenDartDisclosureDocument> document = openDartDisclosureClient.fetchDocumentContent(receiptNumber);
        return document == null ? Optional.empty() : document;
    }

    private record CollectedContent(
            String content,
            List<String> imageUrls,
            String canonicalUrl,
            String contentHash,
            String sourceLicensePolicy
    ) {
        private static CollectedContent fromArticle(OriginalArticleContent content) {
            return new CollectedContent(
                    content.content(),
                    content.imageUrls(),
                    content.canonicalUrl(),
                    content.contentHash(),
                    content.sourceLicensePolicy());
        }

        private static CollectedContent fromDisclosure(OpenDartDisclosureDocument document) {
            return new CollectedContent(
                    document.content(),
                    List.of(),
                    "",
                    document.contentHash(),
                    document.sourceLicensePolicy());
        }

        private static CollectedContent discoveryOnly(String originalUrl) {
            return new CollectedContent("", List.of(), originalUrl, "", "DISCOVERY_ONLY");
        }

        private static CollectedContent officialDisclosure(String originalUrl) {
            return new CollectedContent("", List.of(), originalUrl, "", "OFFICIAL_DISCLOSURE");
        }
    }

    private static class CollectionCounters {
        private int collectedNewsCount;
        private int collectedDisclosureCount;
        private int skippedDuplicateCount;
        private int failedAnalysisCount;
    }
}

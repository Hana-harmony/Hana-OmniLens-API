package com.hana.omnilens.alert.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(AlertProviderCollectionService.class);
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
            if (!isStockRelevantArticle(stock, article, fullContent)) {
                log.info(
                        "Skipping stock news because collected article does not mention requested stock: stockCode={}, url={}",
                        stock.stockCode(),
                        article.originalUrl());
                continue;
            }
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

    private boolean isStockRelevantArticle(
            StockSummary stock,
            NaverNewsArticle article,
            CollectedContent fullContent) {
        String text = String.join(" ",
                article.title() == null ? "" : article.title(),
                fullContent != null && StringUtils.hasText(fullContent.content())
                        ? fullContent.content()
                        : article.snippet() == null ? "" : article.snippet());
        String normalized = normalizeStockMatchText(text);
        if (!StringUtils.hasText(normalized)) {
            return false;
        }
        List<String> candidates = stockMentionCandidates(stock).stream()
                .map(this::normalizeStockMatchText)
                .filter(StringUtils::hasText)
                .toList();
        String normalizedTitle = normalizeStockMatchText(article.title());
        if (candidates.stream().anyMatch(candidate -> containsStockMention(normalizedTitle, candidate))) {
            return true;
        }
        if (fullContent != null && StringUtils.hasText(fullContent.content())) {
            String normalizedContent = normalizeStockMatchText(fullContent.content());
            return candidates.stream().anyMatch(candidate -> stockMentionCount(normalizedContent, candidate) >= 2);
        }
        return candidates.stream().anyMatch(candidate -> containsStockMention(normalized, candidate));
    }

    private List<String> stockMentionCandidates(StockSummary stock) {
        List<String> candidates = new ArrayList<>();
        candidates.add(stock.stockName());
        candidates.add(stock.stockNameEn());
        return candidates.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
    }

    private boolean containsStockMention(String normalizedText, String normalizedCandidate) {
        if (!StringUtils.hasText(normalizedCandidate)) {
            return false;
        }
        if (containsHangul(normalizedCandidate)) {
            return normalizedText.contains(normalizedCandidate);
        }
        return Pattern.compile(
                        "(^|[^a-z0-9])" + Pattern.quote(normalizedCandidate) + "([^a-z0-9]|$)",
                        Pattern.CASE_INSENSITIVE)
                .matcher(normalizedText)
                .find();
    }

    private int stockMentionCount(String normalizedText, String normalizedCandidate) {
        if (!StringUtils.hasText(normalizedText) || !StringUtils.hasText(normalizedCandidate)) {
            return 0;
        }
        if (containsHangul(normalizedCandidate)) {
            int count = 0;
            int fromIndex = 0;
            while (true) {
                int index = normalizedText.indexOf(normalizedCandidate, fromIndex);
                if (index < 0) {
                    return count;
                }
                count++;
                fromIndex = index + normalizedCandidate.length();
            }
        }
        Matcher matcher = Pattern.compile(
                        "(^|[^a-z0-9])" + Pattern.quote(normalizedCandidate) + "([^a-z0-9]|$)",
                        Pattern.CASE_INSENSITIVE)
                .matcher(normalizedText);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private boolean containsHangul(String value) {
        return Pattern.compile("[가-힣]").matcher(value).find();
    }

    private String normalizeStockMatchText(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value
                .replaceAll("<[^>]+>", " ")
                .replace('\u00a0', ' ')
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase(Locale.ROOT);
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
        } catch (RuntimeException exception) {
            log.warn(
                    "Skipping alert article because analysis or translation failed: sourceType={}, stockCode={}, url={}",
                    sourceType,
                    stock.stockCode(),
                    originalUrl,
                    exception);
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

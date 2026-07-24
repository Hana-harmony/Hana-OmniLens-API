package com.hana.omniconnect.alert.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import com.hana.omniconnect.alert.api.AlertAnalysisPublishRequest;
import com.hana.omniconnect.alert.api.AlertCollectPublishRequest;
import com.hana.omniconnect.alert.api.AlertCollectPublishResponse;
import com.hana.omniconnect.alert.api.AlertPublishRequest;
import com.hana.omniconnect.alert.domain.AlertEvent;
import com.hana.omniconnect.market.application.StockMasterRepository;
import com.hana.omniconnect.market.domain.StockSummary;
import com.hana.omniconnect.provider.disclosure.OpenDartDisclosure;
import com.hana.omniconnect.provider.disclosure.OpenDartDisclosureClient;
import com.hana.omniconnect.provider.news.NaverNewsArticle;
import com.hana.omniconnect.provider.news.NaverNewsClient;
import com.hana.omniconnect.provider.news.OriginalArticleClient;
import com.hana.omniconnect.provider.news.OriginalArticleContent;

@Service
public class AlertProviderCollectionService {

    private static final Logger log = LoggerFactory.getLogger(AlertProviderCollectionService.class);
    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");
    private static final int NAVER_STOCK_NEWS_FETCH_MULTIPLIER = 5;
    private static final int NAVER_STOCK_NEWS_MAX_DISPLAY = 100;
    private static final int MAX_NEWS_CANDIDATES_PER_STOCK_RUN = 16;
    private static final int MAX_NEWS_CANDIDATES_PER_QUERY = 4;
    private static final int MAX_BOOTSTRAP_QUERIES_PER_STOCK_RUN = 3;
    private static final int MIN_COMPLETE_ARTICLE_CHARS = 180;
    private static final int MIN_COMPLETE_ARTICLE_SENTENCES = 2;
    private static final int MAX_DISCLOSURE_CANDIDATES_PER_STOCK = 20;
    private static final Duration COLLECTION_LEASE_DURATION = Duration.ofHours(6);
    private static final Duration INCREMENTAL_COLLECTION_OVERLAP = Duration.ofHours(24);
    private static final int MAX_NEW_EVENTS_PER_SOURCE_RUN = 100;
    private static final List<String> STOCK_MARKET_CONTEXT_KEYWORDS = List.of(
            "주가", "주식", "증시", "코스피", "코스닥", "상장", "공시", "거래", "매수", "매도",
            "순매수", "순매도", "기관", "외국인", "투자", "증권", "목표주가", "투자의견", "리포트",
            "컨센서스", "실적", "영업이익", "시가총액", "상한가", "하한가", "공매도", "자사주",
            "신고가", "신저가", "거래정지", "상장폐지", "매출", "계약", "수주", "합병", "분할",
            "배당", "ir", "급등", "급락", "강세", "약세", "호재", "악재");
    private static final List<String> STRONG_STOCK_MARKET_CONTEXT_KEYWORDS = List.of(
            "주가", "주식", "증시", "코스피", "코스닥", "공시", "매수", "매도", "순매수", "순매도",
            "목표주가", "투자의견", "시가총액", "상한가", "하한가", "공매도", "신고가", "신저가",
            "거래정지", "상장폐지", "급등", "급락");
    private static final List<String> ISSUER_SPECIFIC_TITLE_CONTEXT_KEYWORDS = List.of(
            "목표가", "목표주가", "투자의견", "실적", "영업이익", "매출", "공시", "수주", "계약",
            "자사주", "배당", "급등", "급락", "강세", "약세", "신고가", "신저가", "상한가", "하한가",
            "순매수", "순매도", "상향", "하향", "투자 확대");
    private static final List<String> NON_STOCK_ARTICLE_KEYWORDS = List.of(
            "야구", "kbo", "트윈스", "라이온즈", "이글스", "베어스", "자이언츠", "타이거즈",
            "축구", "농구", "배구", "연예", "배우", "가수", "아이돌", "드라마", "예능", "유튜브",
            "영화", "공연", "콘서트", "시청률", "에버랜드", "워터파크", "페스티벌", "맛집");
    private static final List<String> GENERAL_INVESTMENT_FEATURE_KEYWORDS = List.of(
            "커버스토리", "베스트셀러", "저자", "투자 원칙", "주식 투자 안내서", "주식으로 부자",
            "개미의 스승", "투자자의 스승", "인터뷰", "칼럼", "기고", "에세이");
    private static final List<String> MULTI_TOPIC_NEWS_AGGREGATE_KEYWORDS = List.of(
            "이 시각 핫뉴스", "이시각 핫뉴스", "상위권 뉴스", "다음 소식",
            "마지막 기사", "기사 열어보겠습니다", "어떤 것들이 있을까요", "핫뉴스였습니다");
    private static final List<String> MULTI_ISSUER_NICKNAME_KEYWORDS = List.of(
            "삼전닉스", "삼닉스", "삼닉", "삼전 하이닉스", "삼성전자 sk하이닉스", "삼성전자와 sk하이닉스");
    private static final List<String> GENERIC_MULTI_ISSUER_TITLE_KEYWORDS = List.of(
            "기업 공시", "기업공시", "오늘의 공시", "주요 공시", "공시 모음", "공시모음");

    private final NaverNewsClient naverNewsClient;
    private final OriginalArticleClient originalArticleClient;
    private final OpenDartDisclosureClient openDartDisclosureClient;
    private final StockMasterRepository stockMasterRepository;
    private final AlertAnalysisPublishingService alertAnalysisPublishingService;
    private final AlertDedupeStore alertDedupeStore;
    private final AlertEventRepository alertEventRepository;
    private final DisclosureProcessingService disclosureProcessingService;
    private final NewsProcessingService newsProcessingService;
    private final Clock clock;
    private final ConcurrentHashMap<String, AtomicInteger> bootstrapQueryOffsets = new ConcurrentHashMap<>();

    @Autowired
    public AlertProviderCollectionService(
            NaverNewsClient naverNewsClient,
            OriginalArticleClient originalArticleClient,
            OpenDartDisclosureClient openDartDisclosureClient,
            StockMasterRepository stockMasterRepository,
            AlertAnalysisPublishingService alertAnalysisPublishingService,
            AlertDedupeStore alertDedupeStore,
            AlertEventRepository alertEventRepository,
            DisclosureProcessingService disclosureProcessingService,
            NewsProcessingService newsProcessingService) {
        this(
                naverNewsClient,
                originalArticleClient,
                openDartDisclosureClient,
                stockMasterRepository,
                alertAnalysisPublishingService,
                alertDedupeStore,
                alertEventRepository,
                disclosureProcessingService,
                newsProcessingService,
                Clock.system(KOREA_ZONE));
    }

    AlertProviderCollectionService(
            NaverNewsClient naverNewsClient,
            OriginalArticleClient originalArticleClient,
            OpenDartDisclosureClient openDartDisclosureClient,
            StockMasterRepository stockMasterRepository,
            AlertAnalysisPublishingService alertAnalysisPublishingService,
            AlertDedupeStore alertDedupeStore,
            AlertEventRepository alertEventRepository,
            DisclosureProcessingService disclosureProcessingService,
            NewsProcessingService newsProcessingService,
            Clock clock) {
        this.naverNewsClient = naverNewsClient;
        this.originalArticleClient = originalArticleClient;
        this.openDartDisclosureClient = openDartDisclosureClient;
        this.stockMasterRepository = stockMasterRepository;
        this.alertAnalysisPublishingService = alertAnalysisPublishingService;
        this.alertDedupeStore = alertDedupeStore;
        this.alertEventRepository = alertEventRepository;
        this.disclosureProcessingService = disclosureProcessingService;
        this.newsProcessingService = newsProcessingService;
        this.clock = clock;
    }

    public AlertCollectPublishResponse collectAnalyzeAndPublish(AlertCollectPublishRequest request) {
        return collectAnalyzeAndPublish(request, false);
    }

    public AlertCollectPublishResponse collectIncrementalAnalyzeAndPublish(AlertCollectPublishRequest request) {
        return collectAnalyzeAndPublish(request, true);
    }

    private AlertCollectPublishResponse collectAnalyzeAndPublish(
            AlertCollectPublishRequest request,
            boolean incrementalEnabled) {
        List<StockSummary> stocks = uniqueStocks(request.stockCodes());
        if (stocks.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "no supported stock codes");
        }

        CollectionCounters counters = new CollectionCounters();
        List<AlertEvent> events = new ArrayList<>();
        LocalDate endDate = LocalDate.now(clock);
        LocalDate beginDate = endDate.minusDays(request.effectiveDisclosureLookbackDays());

        for (StockSummary stock : stocks) {
            String leaseKey = "COLLECTION_LEASE:" + request.partnerId() + ":" + stock.stockCode();
            Optional<String> leaseToken = alertDedupeStore.acquireLease(
                    leaseKey,
                    COLLECTION_LEASE_DURATION);
            if (leaseToken.isEmpty()) {
                log.info(
                        "Skipping overlapping stock collection: partnerId={}, stockCode={}",
                        request.partnerId(),
                        stock.stockCode());
                continue;
            }
            try {
                // 공식 공시는 먼저 영속 작업으로 등록해 Qwen 장애가 수집 손실로 이어지지 않게 한다.
                queueDisclosures(request, stock, beginDate, endDate, incrementalEnabled, counters);
                collectNews(request, stock, incrementalEnabled, counters, events);
            } finally {
                // 임대 토큰 소유자만 잠금을 해제해 만료 후 재획득 경쟁을 방지한다.
                alertDedupeStore.releaseLease(leaseKey, leaseToken.orElseThrow());
            }
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

    private void collectNews(
            AlertCollectPublishRequest request,
            StockSummary stock,
            boolean incrementalEnabled,
            CollectionCounters counters,
            List<AlertEvent> events) {
        int targetDisplay = request.effectiveNewsDisplay();
        Set<String> seenArticleKeys = new LinkedHashSet<>();
        CollectionProgress progress = initialCollectionProgress(
                request.partnerId(),
                stock.stockCode(),
                "NEWS",
                targetDisplay,
                incrementalEnabled);
        int satisfiedForStock = progress.satisfiedCount();
        int publishedForStock = 0;
        List<String> queries = progress.incremental()
                ? List.of(stock.stockName(), stock.stockName() + " 주가")
                : bootstrapQueries(stock, incrementalEnabled);
        int searchDisplay = progress.incremental()
                ? NAVER_STOCK_NEWS_MAX_DISPLAY
                : stockNewsSearchDisplay(targetDisplay);
        int inspectedForStock = 0;
        queryLoop:
        for (String query : queries) {
            if (!progress.incremental() && satisfiedForStock >= targetDisplay) {
                break;
            }
            int inspectedForQuery = 0;
            List<NaverNewsArticle> articles;
            try {
                articles = Optional
                        .ofNullable(naverNewsClient.search(query, searchDisplay))
                        .orElse(List.of());
            } catch (RestClientException exception) {
                log.warn(
                        "Skipping stock news query because provider request failed: stockCode={}, query={}",
                        stock.stockCode(),
                        query,
                        exception);
                counters.failedAnalysisCount++;
                break;
            }
            counters.collectedNewsCount += articles.size();
            for (NaverNewsArticle article : articles) {
                if ((!progress.incremental() && satisfiedForStock >= targetDisplay)
                        || publishedForStock >= MAX_NEW_EVENTS_PER_SOURCE_RUN) {
                    break;
                }
                if (progress.incremental() && isBeforeBoundary(article.publishedAt(), progress.boundary())) {
                    continue;
                }
                if (inspectedForStock >= MAX_NEWS_CANDIDATES_PER_STOCK_RUN) {
                    break queryLoop;
                }
                if (inspectedForQuery >= MAX_NEWS_CANDIDATES_PER_QUERY) {
                    break;
                }
                if (!seenArticleKeys.add(newsArticleKey(article))) {
                    counters.skippedDuplicateCount++;
                    continue;
                }
                inspectedForStock++;
                inspectedForQuery++;
                PublicationResult result = processNewsArticle(
                        request, stock, counters, events, article, incrementalEnabled);
                if (result == PublicationResult.PUBLISHED) {
                    publishedForStock++;
                }
                if (!progress.incremental()
                        && (result == PublicationResult.PUBLISHED
                                || progress.latestCheckOnly() && result == PublicationResult.ALREADY_STORED)) {
                    satisfiedForStock++;
                }
            }
        }
    }

    private PublicationResult processNewsArticle(
            AlertCollectPublishRequest request,
            StockSummary stock,
            CollectionCounters counters,
            List<AlertEvent> events,
            NaverNewsArticle article,
            boolean durableQueue) {
            if (isAlreadyStored(request.partnerId(), stock.stockCode(), "NEWS", article.originalUrl())) {
                counters.skippedDuplicateCount++;
                return PublicationResult.ALREADY_STORED;
            }
            Optional<OriginalArticleContent> fetchedContent = originalArticleClient.fetch(article.originalUrl());
            if (fetchedContent.isEmpty()) {
                log.info(
                        "Skipping stock news because original article body was not extracted: stockCode={}, url={}",
                        stock.stockCode(),
                        article.originalUrl());
                return PublicationResult.SKIPPED;
            }
            CollectedContent fullContent = CollectedContent.fromArticle(fetchedContent.orElseThrow());
            if (!isCompleteOriginalArticle(fullContent.content())) {
                log.info(
                        "Skipping stock news because original article body is incomplete: stockCode={}, contentLength={}, url={}",
                        stock.stockCode(),
                        fullContent.content() == null ? 0 : fullContent.content().length(),
                        article.originalUrl());
                return PublicationResult.SKIPPED;
            }
            if (!isStockRelevantArticle(stock, article, fullContent)) {
                log.info(
                        "Skipping stock news because collected article is not stock-market relevant: stockCode={}, url={}",
                        stock.stockCode(),
                        article.originalUrl());
                return PublicationResult.SKIPPED;
            }
            if (durableQueue) {
                boolean enqueued = newsProcessingService.enqueue(
                        request.partnerId(), stock,
                        preferredArticleTitle(article.title(), fullContent), article.snippet(),
                        article.originalUrl(), article.publishedAt(), fullContent.content(),
                        fullContent.imageUrls(), fullContent.canonicalUrl(), fullContent.contentHash(),
                        fullContent.sourceLicensePolicy());
                if (!enqueued) {
                    counters.skippedDuplicateCount++;
                    return PublicationResult.ALREADY_STORED;
                }
                return PublicationResult.PUBLISHED;
            }
            return publishCollectedAlert(
                    request.partnerId(), stock, "NEWS",
                    preferredArticleTitle(article.title(), fullContent), article.snippet(), fullContent,
                    article.originalUrl(), article.publishedAt(), counters, events);
    }

    private int stockNewsSearchDisplay(int targetDisplay) {
        return Math.min(
                NAVER_STOCK_NEWS_MAX_DISPLAY,
                Math.max(targetDisplay + 10, targetDisplay * (NAVER_STOCK_NEWS_FETCH_MULTIPLIER - 2)));
    }

    private List<String> stockNewsQueries(StockSummary stock) {
        List<String> queries = new ArrayList<>();
        queries.add(stock.stockName() + " 주가");
        queries.add(stock.stockName() + " " + stock.stockCode());
        queries.add(stock.stockCode() + " 주가");
        queries.add(stock.stockName() + " 실적");
        queries.add(stock.stockName() + " 공시");
        queries.add(stock.stockName() + " 목표주가");
        queries.add(stock.stockName() + " 수주");
        queries.add(stock.stockName() + " 외국인");
        queries.add(stock.stockName() + " 기관");
        queries.add(stock.stockName() + " 영업이익");
        queries.add(stock.stockName() + " 업황");
        stockSectorNewsKeywords(stock).stream()
                .map(keyword -> stock.stockName() + " " + keyword)
                .forEach(queries::add);
        stockAliases(stock).stream()
                .filter(alias -> alias.length() >= 3)
                .limit(2)
                .map(alias -> alias + " 주가")
                .forEach(queries::add);
        return queries.stream().distinct().toList();
    }

    private List<String> nextBootstrapQueries(StockSummary stock) {
        List<String> queries = stockNewsQueries(stock);
        if (queries.size() <= MAX_BOOTSTRAP_QUERIES_PER_STOCK_RUN) {
            return queries;
        }
        AtomicInteger offset = bootstrapQueryOffsets.computeIfAbsent(
                stock.stockCode(),
                ignored -> new AtomicInteger());
        int start = Math.floorMod(
                offset.getAndAdd(MAX_BOOTSTRAP_QUERIES_PER_STOCK_RUN),
                queries.size());
        List<String> selected = new ArrayList<>(MAX_BOOTSTRAP_QUERIES_PER_STOCK_RUN);
        for (int index = 0; index < MAX_BOOTSTRAP_QUERIES_PER_STOCK_RUN; index++) {
            selected.add(queries.get((start + index) % queries.size()));
        }
        return List.copyOf(selected);
    }

    private List<String> bootstrapQueries(StockSummary stock, boolean scheduledIncrementalCollection) {
        if (!scheduledIncrementalCollection) {
            return stockNewsQueries(stock);
        }
        return nextBootstrapQueries(stock);
    }

    private List<String> stockSectorNewsKeywords(StockSummary stock) {
        return switch (stock.stockCode()) {
            case "005930", "000660" -> List.of("반도체", "HBM", "메모리");
            case "373220", "051910" -> List.of("배터리", "전기차", "양극재");
            case "207940" -> List.of("바이오", "위탁생산", "CDMO");
            case "005380", "000270" -> List.of("전기차", "수출", "판매량");
            case "005490" -> List.of("철강", "수출", "원자재");
            case "035420", "035720" -> List.of("AI", "플랫폼", "광고");
            default -> List.of();
        };
    }

    private String newsArticleKey(NaverNewsArticle article) {
        String key = article.originalUrl();
        if (!StringUtils.hasText(key)) {
            key = article.title() + ":" + article.publishedAt();
        }
        return key;
    }

    private String preferredArticleTitle(String searchTitle, CollectedContent fullContent) {
        String pageTitle = fullContent == null ? "" : fullContent.title();
        if (StringUtils.hasText(pageTitle)
                && (!StringUtils.hasText(searchTitle)
                || hasTruncationMarker(searchTitle)
                || pageTitle.length() >= searchTitle.length() + 12)) {
            return pageTitle;
        }
        return searchTitle;
    }

    private boolean hasTruncationMarker(String value) {
        return StringUtils.hasText(value) && (value.contains("...") || value.contains("…"));
    }

    private boolean isCompleteOriginalArticle(String content) {
        String normalized = normalizeStockMatchText(content);
        return normalized.length() >= MIN_COMPLETE_ARTICLE_CHARS
                && sentenceCount(normalized) >= MIN_COMPLETE_ARTICLE_SENTENCES
                && !looksLikeEmbeddedJsonContent(content);
    }

    private boolean looksLikeEmbeddedJsonContent(String content) {
        if (!StringUtils.hasText(content)) {
            return false;
        }
        String trimmed = content.stripLeading();
        if (!(trimmed.startsWith("[{") || trimmed.startsWith("{\""))) {
            return false;
        }
        return StringUtils.countOccurrencesOf(trimmed.substring(0, Math.min(trimmed.length(), 1_000)), "\":")
                >= 3;
    }

    private boolean isStockRelevantArticle(
            StockSummary stock,
            NaverNewsArticle article,
            CollectedContent fullContent) {
        String normalizedTitle = normalizeStockMatchText(
                StringUtils.hasText(fullContent == null ? "" : fullContent.title())
                        ? fullContent.title()
                        : article.title());
        String normalizedSnippet = normalizeStockMatchText(article.snippet());
        String normalizedContent = normalizeStockMatchText(fullContent == null ? "" : fullContent.content());
        String normalizedTitleAndSnippet = normalizeStockMatchText(normalizedTitle + " " + normalizedSnippet);
        String normalizedAll = normalizeStockMatchText(normalizedTitleAndSnippet + " " + normalizedContent);
        if (!StringUtils.hasText(normalizedAll)) {
            return false;
        }
        List<String> candidates = stockMentionCandidates(stock).stream()
                .map(this::normalizeStockMatchText)
                .filter(StringUtils::hasText)
                .toList();
        boolean hasStockCode = containsStockMention(normalizedAll, stock.stockCode());
        boolean hasStockCodeInTitle = containsStockMention(normalizedTitle, stock.stockCode());
        boolean hasStockCodeInTitleOrSnippet = containsStockMention(normalizedTitleAndSnippet, stock.stockCode());
        boolean hasStockNameInTitleOrSnippet = candidates.stream()
                .anyMatch(candidate -> containsStockMention(normalizedTitleAndSnippet, candidate));
        boolean hasStockNameInTitle = candidates.stream()
                .anyMatch(candidate -> containsStockMention(normalizedTitle, candidate));
        boolean hasStockNameInContent = candidates.stream()
                .anyMatch(candidate -> containsStockMention(normalizedContent, candidate));
        if (startsWithDifferentListedIssuer(normalizedTitle, stock, candidates)) {
            return false;
        }
        if (mentionsDifferentListedIssuerBeforeRequestedTitle(normalizedTitle, stock, candidates)) {
            return false;
        }
        if (containsAny(normalizedTitleAndSnippet, MULTI_ISSUER_NICKNAME_KEYWORDS)) {
            return false;
        }
        if (!hasStockCode && !hasStockNameInTitleOrSnippet && !hasStockNameInContent) {
            return false;
        }
        // 종목별 수집은 요청 종목이 제목의 중심 주어일 때만 분석기로 보낸다.
        if (!hasStockCodeInTitleOrSnippet && !hasStockNameInTitle) {
            return false;
        }

        boolean hasMarketContext = containsAny(normalizedAll, STOCK_MARKET_CONTEXT_KEYWORDS);
        if (!hasMarketContext) {
            return false;
        }
        if (!hasStockCode && !hasStockNameInTitleOrSnippet) {
            return false;
        }
        if (containsAny(normalizedAll, MULTI_TOPIC_NEWS_AGGREGATE_KEYWORDS)) {
            return false;
        }

        boolean hasDirectIssuerInTitle = hasStockCodeInTitle || hasStockNameInTitle;
        if (!hasDirectIssuerInTitle
                && containsAny(normalizedTitle, GENERIC_MULTI_ISSUER_TITLE_KEYWORDS)) {
            return false;
        }
        if (!hasDirectIssuerInTitle) {
            if (containsAny(normalizedTitleAndSnippet + " " + normalizedContent, GENERAL_INVESTMENT_FEATURE_KEYWORDS)) {
                return false;
            }
            if (!containsAny(normalizedTitle, ISSUER_SPECIFIC_TITLE_CONTEXT_KEYWORDS)) {
                return false;
            }
        }

        boolean ambiguousStockName = candidates.stream().anyMatch(this::isAmbiguousStockMention);
        boolean strongTitleContext = containsAny(normalizedTitleAndSnippet, STRONG_STOCK_MARKET_CONTEXT_KEYWORDS);
        if (ambiguousStockName && !hasStockCode && !(hasStockNameInTitleOrSnippet && strongTitleContext)) {
            return false;
        }

        boolean titleLooksNonStock = containsAny(normalizedTitleAndSnippet, NON_STOCK_ARTICLE_KEYWORDS);
        return !titleLooksNonStock || hasStockCode || strongTitleContext;
    }

    private boolean startsWithDifferentListedIssuer(
            String normalizedTitle,
            StockSummary requestedStock,
            List<String> requestedCandidates) {
        if (!StringUtils.hasText(normalizedTitle)) {
            return false;
        }
        Matcher matcher = Pattern.compile("^([a-z0-9가-힣&().\\s]{2,30})[,，]").matcher(normalizedTitle);
        if (!matcher.find()) {
            return false;
        }
        String leadingIssuer = normalizeStockMatchText(matcher.group(1));
        if (!StringUtils.hasText(leadingIssuer)) {
            return false;
        }
        boolean requestedIssuerLeadsTitle = requestedCandidates.stream()
                .anyMatch(candidate -> containsStockMention(leadingIssuer, candidate)
                        || containsStockMention(candidate, leadingIssuer));
        if (requestedIssuerLeadsTitle) {
            return false;
        }
        boolean requestedMentionedAfterLeadingIssuer = requestedCandidates.stream()
                .anyMatch(candidate -> containsStockMention(normalizedTitle.substring(matcher.end()), candidate));
        if (!requestedMentionedAfterLeadingIssuer) {
            return false;
        }
        return stockMasterRepository.search(leadingIssuer).stream()
                .filter(other -> !requestedStock.stockCode().equals(other.stockCode()))
                .anyMatch(other -> isSameListedIssuerName(leadingIssuer, other));
    }

    private boolean mentionsDifferentListedIssuerBeforeRequestedTitle(
            String normalizedTitle,
            StockSummary requestedStock,
            List<String> requestedCandidates) {
        int requestedIndex = earliestRequestedMentionIndex(normalizedTitle, requestedStock, requestedCandidates);
        if (requestedIndex <= 0) {
            return false;
        }
        String prefix = normalizedTitle.substring(0, requestedIndex);
        return Pattern.compile("[a-z0-9가-힣&.]+")
                .matcher(prefix)
                .results()
                .map(MatchResult::group)
                .filter(token -> token.length() >= 2)
                .filter(token -> !token.matches("\\d+"))
                .anyMatch(token -> stockMasterRepository.search(token).stream()
                        .filter(other -> !requestedStock.stockCode().equals(other.stockCode()))
                        .anyMatch(other -> isSameListedIssuerName(token, other)));
    }

    private int earliestRequestedMentionIndex(
            String normalizedTitle,
            StockSummary stock,
            List<String> requestedCandidates) {
        int earliest = mentionIndex(normalizedTitle, stock.stockCode());
        for (String candidate : requestedCandidates) {
            int index = mentionIndex(normalizedTitle, candidate);
            if (index >= 0 && (earliest < 0 || index < earliest)) {
                earliest = index;
            }
        }
        return earliest;
    }

    private int mentionIndex(String normalizedText, String candidate) {
        String normalizedCandidate = normalizeStockMatchText(candidate);
        if (!StringUtils.hasText(normalizedText) || !StringUtils.hasText(normalizedCandidate)) {
            return -1;
        }
        if (containsHangul(normalizedCandidate)) {
            return normalizedText.indexOf(normalizedCandidate);
        }
        Matcher matcher = Pattern.compile(
                        "(^|[^a-z0-9])" + Pattern.quote(normalizedCandidate) + "([^a-z0-9]|$)",
                        Pattern.CASE_INSENSITIVE)
                .matcher(normalizedText);
        return matcher.find() ? matcher.start() : -1;
    }

    private boolean isSameListedIssuerName(String normalizedLeadingIssuer, StockSummary stock) {
        return stockMentionCandidates(stock).stream()
                .map(this::normalizeStockMatchText)
                .filter(candidate -> candidate.length() >= 2)
                .anyMatch(candidate -> normalizedLeadingIssuer.equals(candidate)
                        || normalizedLeadingIssuer.contains(candidate)
                        || candidate.contains(normalizedLeadingIssuer));
    }

    private List<String> stockMentionCandidates(StockSummary stock) {
        List<String> candidates = new ArrayList<>();
        candidates.add(stock.stockName());
        candidates.add(stock.stockNameEn());
        candidates.addAll(stockAliases(stock));
        return candidates.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
    }

    private List<String> stockAliases(StockSummary stock) {
        return switch (stock.stockCode()) {
            case "005930" -> List.of("삼전", "Samsung Elec");
            case "000660" -> List.of("하이닉스", "Hynix");
            case "373220" -> List.of("LG엔솔", "엘지엔솔", "엔솔");
            case "207940" -> List.of("삼바", "삼성바이오");
            case "005380" -> List.of("현대자동차");
            case "000270" -> List.of("기아차");
            case "005490" -> List.of("포스코홀딩스", "포스코");
            case "051910" -> List.of("엘지화학");
            case "035420" -> List.of("네이버");
            case "035720" -> List.of("카카오");
            case "015760" -> List.of("한전");
            case "036460" -> List.of("가스공사");
            case "003495" -> List.of("대한항공");
            case "032640" -> List.of("유플러스", "LG U+", "엘지유플러스");
            case "053210" -> List.of("KT스카이라이프");
            default -> List.of();
        };
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

    private boolean isAmbiguousStockMention(String normalizedCandidate) {
        if (!StringUtils.hasText(normalizedCandidate)) {
            return false;
        }
        String stripped = normalizedCandidate.replaceAll("\\s+", "");
        return stripped.length() <= 3
                || List.of("lg", "sbs", "knn", "naver", "sk", "cj", "kt", "kcc")
                        .contains(stripped);
    }

    private boolean containsAny(String normalizedText, List<String> keywords) {
        if (!StringUtils.hasText(normalizedText)) {
            return false;
        }
        return keywords.stream()
                .map(this::normalizeStockMatchText)
                .filter(StringUtils::hasText)
                .anyMatch(keyword -> containsStockMention(normalizedText, keyword));
    }

    private int sentenceCount(String text) {
        return (int) Pattern.compile("[.!?。]|(?:다|요|니다|습니다|한다|했다|됐다|된다)(?=\\s|$)")
                .splitAsStream(text)
                .filter(value -> value != null && !value.isBlank())
                .count();
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

    private void queueDisclosures(
            AlertCollectPublishRequest request,
            StockSummary stock,
            LocalDate beginDate,
            LocalDate endDate,
            boolean incrementalEnabled,
            CollectionCounters counters) {
        if (stock.dartCorpCode() == null || stock.dartCorpCode().isBlank()) {
            return;
        }

        List<OpenDartDisclosure> disclosures;
        try {
            disclosures = Optional.ofNullable(openDartDisclosureClient.search(
                            stock.dartCorpCode(),
                            beginDate,
                            endDate))
                    .orElse(List.of());
        } catch (RestClientException exception) {
            log.warn(
                    "Skipping stock disclosures because provider request failed: stockCode={}, dartCorpCode={}",
                    stock.stockCode(),
                    stock.dartCorpCode(),
                    exception);
            counters.failedAnalysisCount++;
            return;
        }
        counters.collectedDisclosureCount += disclosures.size();
        int targetDisplay = request.effectiveNewsDisplay();
        CollectionProgress progress = initialCollectionProgress(
                request.partnerId(),
                stock.stockCode(),
                "DISCLOSURE",
                targetDisplay,
                incrementalEnabled);
        int candidateCount = 0;
        int candidateLimit = progress.latestCheckOnly() ? 1 : MAX_DISCLOSURE_CANDIDATES_PER_STOCK;
        for (OpenDartDisclosure disclosure : latestDisclosures(disclosures)) {
            if (candidateCount >= candidateLimit) {
                break;
            }
            Instant publishedAt = disclosure.receivedAt().atStartOfDay(KOREA_ZONE).toInstant();
            if (progress.incremental() && isBeforeBoundary(publishedAt, progress.boundary())) {
                continue;
            }
            if (isAlreadyStored(
                    request.partnerId(),
                    stock.stockCode(),
                    "DISCLOSURE",
                    disclosure.originalUrl())) {
                counters.skippedDuplicateCount++;
                continue;
            }
            candidateCount++;
            boolean enqueued = disclosureProcessingService.enqueue(
                    request.partnerId(),
                    stock,
                    disclosure,
                    publishedAt);
            if (!enqueued) {
                counters.skippedDuplicateCount++;
            }
        }
    }

    private List<OpenDartDisclosure> latestDisclosures(List<OpenDartDisclosure> disclosures) {
        return disclosures.stream()
                .sorted(Comparator
                        .comparing(OpenDartDisclosure::receivedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(OpenDartDisclosure::receiptNumber, Comparator.nullsLast(Comparator.naturalOrder()))
                        .reversed())
                .toList();
    }

    private PublicationResult publishCollectedAlert(
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
        String sourceKey = partnerId + ":COLLECTION:v2:" + stock.stockCode() + ":" + sourceType + ":" + originalUrl;
        if (!alertDedupeStore.markIfFirst(sourceKey)) {
            counters.skippedDuplicateCount++;
            return PublicationResult.SKIPPED;
        }

        String markedAiDuplicateKey = null;
        try {
            AlertPublishRequest analyzedAlert = alertAnalysisPublishingService.analyzeForCollection(
                    new AlertAnalysisPublishRequest(
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
                            stockAliases(stock)))));
            if (!stock.stockCode().equals(analyzedAlert.stockCode())) {
                log.info(
                        "Skipping alert article because analysis matched a different stock: requestedStockCode={}, analyzedStockCode={}, sourceType={}, url={}",
                        stock.stockCode(),
                        analyzedAlert.stockCode(),
                        sourceType,
                        originalUrl);
                alertDedupeStore.remove(sourceKey);
                counters.failedAnalysisCount++;
                return PublicationResult.SKIPPED;
            }
            if (!alertAnalysisPublishingService.isPublishReady(analyzedAlert)) {
                log.info(
                        "Skipping alert article until the complete English article is ready: stockCode={}, sourceType={}, url={}",
                        stock.stockCode(),
                        sourceType,
                        originalUrl);
                alertDedupeStore.remove(sourceKey);
                counters.failedAnalysisCount++;
                return PublicationResult.SKIPPED;
            }
            String aiDuplicateKey = aiDuplicateKey(partnerId, analyzedAlert);
            if ("NEWS".equalsIgnoreCase(analyzedAlert.sourceType())
                    && StringUtils.hasText(analyzedAlert.duplicateKey())
                    && alertEventRepository.findByDuplicateIdentity(
                                    partnerId,
                                    analyzedAlert.stockCode(),
                                    analyzedAlert.sourceType(),
                                    analyzedAlert.duplicateKey())
                            .isPresent()) {
                alertDedupeStore.remove(sourceKey);
                counters.skippedDuplicateCount++;
                return PublicationResult.ALREADY_STORED;
            }
            if (aiDuplicateKey != null && !alertDedupeStore.markIfFirst(aiDuplicateKey)) {
                alertDedupeStore.remove(sourceKey);
                counters.skippedDuplicateCount++;
                return PublicationResult.SKIPPED;
            }
            markedAiDuplicateKey = aiDuplicateKey;
            events.add(alertAnalysisPublishingService.publishAnalyzed(analyzedAlert));
            return PublicationResult.PUBLISHED;
        } catch (ResponseStatusException exception) {
            alertDedupeStore.remove(sourceKey);
            removeMarkedAiDuplicateKey(markedAiDuplicateKey);
            counters.failedAnalysisCount++;
            return PublicationResult.SKIPPED;
        } catch (RuntimeException exception) {
            log.warn(
                    "Skipping alert article because analysis or translation failed: sourceType={}, stockCode={}, url={}",
                    sourceType,
                    stock.stockCode(),
                    originalUrl,
                    exception);
            alertDedupeStore.remove(sourceKey);
            removeMarkedAiDuplicateKey(markedAiDuplicateKey);
            counters.failedAnalysisCount++;
            return PublicationResult.SKIPPED;
        }
    }

    private void removeMarkedAiDuplicateKey(String markedAiDuplicateKey) {
        if (StringUtils.hasText(markedAiDuplicateKey)) {
            alertDedupeStore.remove(markedAiDuplicateKey);
        }
    }

    private CollectionProgress initialCollectionProgress(
            String partnerId,
            String stockCode,
            String sourceType,
            int targetDisplay,
            boolean incrementalEnabled) {
        int existingCount = alertEventRepository.countByPartnerStockAndSourceType(
                partnerId,
                stockCode,
                sourceType);
        if (existingCount >= targetDisplay && incrementalEnabled) {
            Instant boundary = alertEventRepository.findLatestByPartnerStockAndSourceType(
                            partnerId,
                            stockCode,
                            sourceType)
                    .map(AlertEvent::publishedAt)
                    .map(value -> value.minus(INCREMENTAL_COLLECTION_OVERLAP))
                    .orElse(Instant.EPOCH);
            return new CollectionProgress(existingCount, true, false, boundary);
        }
        if (existingCount >= targetDisplay) {
            return new CollectionProgress(Math.max(0, targetDisplay - 1), false, true, Instant.EPOCH);
        }
        return new CollectionProgress(Math.max(0, existingCount), false, false, Instant.EPOCH);
    }

    private boolean isBeforeBoundary(Instant publishedAt, Instant boundary) {
        return publishedAt != null && boundary != null && publishedAt.isBefore(boundary);
    }

    private boolean isAlreadyStored(
            String partnerId,
            String stockCode,
            String sourceType,
            String originalUrl) {
        return StringUtils.hasText(originalUrl)
                && alertEventRepository.findBySourceIdentity(
                                partnerId,
                                stockCode,
                                sourceType,
                                originalUrl)
                        .filter(this::isPublishReadyEvent)
                        .isPresent();
    }

    private boolean isPublishReadyEvent(AlertEvent event) {
        return event != null
                && StringUtils.hasText(event.originalContent())
                && EnglishNewsQualityGate.hasUsableEnglishSummaryLines(event.summaryLines())
                && EnglishNewsQualityGate.hasUsableEnglishText(event.translatedContent())
                && !EnglishNewsQualityGate.looksLikeSummaryOnlyContent(
                        event.translatedContent(),
                        event.summaryLines(),
                        event.translatedSummary(),
                        event.originalContent());
    }

    private static String aiDuplicateKey(String partnerId, AlertPublishRequest request) {
        if (!StringUtils.hasText(request.duplicateKey())) {
            return null;
        }
        String key = partnerId + ":AI:v2:" + request.stockCode() + ":" + request.sourceType()
                + ":" + request.duplicateKey();
        if ("DISCLOSURE".equalsIgnoreCase(request.sourceType())) {
            // 반복형 공시 제목이 같아도 접수번호별 공식 문서는 서로 다른 이벤트다.
            key += ":" + sha256Hex(request.originalUrl());
        }
        return key;
    }

    private static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private record CollectedContent(
            String content,
            List<String> imageUrls,
            String canonicalUrl,
            String contentHash,
            String sourceLicensePolicy,
            String title
    ) {
        private CollectedContent(
                String content,
                List<String> imageUrls,
                String canonicalUrl,
                String contentHash,
                String sourceLicensePolicy) {
            this(content, imageUrls, canonicalUrl, contentHash, sourceLicensePolicy, "");
        }

        private static CollectedContent fromArticle(OriginalArticleContent content) {
            return new CollectedContent(
                    content.content(),
                    content.imageUrls(),
                    content.canonicalUrl(),
                    content.contentHash(),
                    content.sourceLicensePolicy(),
                    content.title());
        }

    }

    private static class CollectionCounters {
        private int collectedNewsCount;
        private int collectedDisclosureCount;
        private int skippedDuplicateCount;
        private int failedAnalysisCount;
    }

    private enum PublicationResult {
        PUBLISHED,
        ALREADY_STORED,
        SKIPPED
    }

    private record CollectionProgress(
            int satisfiedCount,
            boolean incremental,
            boolean latestCheckOnly,
            Instant boundary) {
    }
}

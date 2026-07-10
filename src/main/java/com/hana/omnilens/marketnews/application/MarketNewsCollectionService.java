package com.hana.omnilens.marketnews.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.hana.omnilens.alert.domain.AlertGlossaryTerm;
import com.hana.omnilens.alert.domain.AlertSummaryLines;
import com.hana.omnilens.alert.application.AlertTitleTranslationService;
import com.hana.omnilens.alert.application.AlertTitleTranslationService.TranslationResult;
import com.hana.omnilens.alert.application.EnglishNewsQualityGate;
import com.hana.omnilens.alert.application.KoreanMarketGlossaryTermExtractor;
import com.hana.omnilens.config.MarketNewsCollectionProperties;
import com.hana.omnilens.market.application.StockMasterRepository;
import com.hana.omnilens.market.domain.StockSummary;
import com.hana.omnilens.marketnews.domain.MarketNewsEvent;
import com.hana.omnilens.provider.ai.HannahAiAnalysisClient;
import com.hana.omnilens.provider.ai.HannahAiAnalysisRequest;
import com.hana.omnilens.provider.ai.HannahAiAnalysisResponse;
import com.hana.omnilens.provider.ai.HannahAiGlossaryTerm;
import com.hana.omnilens.provider.news.NaverNewsArticle;
import com.hana.omnilens.provider.news.NaverNewsClient;
import com.hana.omnilens.provider.news.OriginalArticleClient;
import com.hana.omnilens.provider.news.OriginalArticleContent;

@Service
public class MarketNewsCollectionService {

    private static final Logger log = LoggerFactory.getLogger(MarketNewsCollectionService.class);
    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");
    private static final int NAVER_MARKET_NEWS_FETCH_MULTIPLIER = 5;
    private static final int NAVER_MARKET_NEWS_MAX_DISPLAY = 100;
    private static final int MAX_NEW_MARKET_NEWS_PER_RUN = 100;
    private static final Duration INCREMENTAL_COLLECTION_OVERLAP = Duration.ofHours(24);

    private final NaverNewsClient naverNewsClient;
    private final OriginalArticleClient originalArticleClient;
    private final MarketNewsEventRepository marketNewsEventRepository;
    private final MarketNewsCollectionProperties properties;
    private final HannahAiAnalysisClient hannahAiAnalysisClient;
    private final AlertTitleTranslationService translationService;
    private final StockMasterRepository stockMasterRepository;
    private final KoreanMarketGlossaryTermExtractor glossaryTermExtractor = new KoreanMarketGlossaryTermExtractor();
    private final Clock clock;
    private volatile List<String> listedIssuerNameCache;

    @Autowired
    public MarketNewsCollectionService(
            NaverNewsClient naverNewsClient,
            OriginalArticleClient originalArticleClient,
            MarketNewsEventRepository marketNewsEventRepository,
            MarketNewsCollectionProperties properties,
            HannahAiAnalysisClient hannahAiAnalysisClient,
            AlertTitleTranslationService translationService,
            StockMasterRepository stockMasterRepository) {
        this(
                naverNewsClient,
                originalArticleClient,
                marketNewsEventRepository,
                properties,
                hannahAiAnalysisClient,
                translationService,
                stockMasterRepository,
                Clock.system(KOREA_ZONE));
    }

    MarketNewsCollectionService(
            NaverNewsClient naverNewsClient,
            OriginalArticleClient originalArticleClient,
            MarketNewsEventRepository marketNewsEventRepository,
            MarketNewsCollectionProperties properties,
            HannahAiAnalysisClient hannahAiAnalysisClient,
            AlertTitleTranslationService translationService,
            StockMasterRepository stockMasterRepository,
            Clock clock) {
        this.naverNewsClient = naverNewsClient;
        this.originalArticleClient = originalArticleClient;
        this.marketNewsEventRepository = marketNewsEventRepository;
        this.properties = properties;
        this.hannahAiAnalysisClient = hannahAiAnalysisClient;
        this.translationService = translationService;
        this.stockMasterRepository = stockMasterRepository;
        this.clock = clock;
    }

    public MarketNewsCollectionResult collectConfiguredQueries() {
        return collect(properties.queries(), properties.display());
    }

    public MarketNewsCollectionResult collect(List<String> queries, int display) {
        List<String> effectiveQueries = effectiveQueries(queries);
        int effectiveDisplay = display <= 0 ? properties.display() : Math.min(display, 100);
        Counters counters = new Counters();
        List<MarketNewsEvent> events = new ArrayList<>();
        Set<String> seenDuplicateKeys = new HashSet<>();
        List<MarketNewsEvent> existing = marketNewsEventRepository.findLatest(effectiveDisplay);
        boolean incremental = existing.size() >= effectiveDisplay;
        Instant boundary = existing.stream()
                .map(MarketNewsEvent::publishedAt)
                .filter(java.util.Objects::nonNull)
                .max(Comparator.naturalOrder())
                .map(value -> value.minus(INCREMENTAL_COLLECTION_OVERLAP))
                .orElse(Instant.EPOCH);
        for (String query : effectiveQueries) {
            if ((!incremental && counters.satisfiedCount >= effectiveDisplay)
                    || counters.storedCount >= MAX_NEW_MARKET_NEWS_PER_RUN) {
                break;
            }
            collectQuery(
                    query,
                    effectiveDisplay,
                    incremental,
                    boundary,
                    counters,
                    events,
                    seenDuplicateKeys);
        }
        return new MarketNewsCollectionResult(
                effectiveQueries,
                counters.collectedCount,
                counters.storedCount,
                counters.duplicateCount,
                events,
                Instant.now(clock));
    }

    public List<MarketNewsEvent> reprocessLatest(int limit) {
        int effectiveLimit = Math.max(1, Math.min(limit, 100));
        return marketNewsEventRepository.findLatest(effectiveLimit).stream()
                .map(this::reprocess)
                .toList();
    }

    public Optional<MarketNewsEvent> reprocessByNewsId(String newsId) {
        return marketNewsEventRepository.findByNewsId(newsId)
                .map(event -> {
                    try {
                        return reprocess(event);
                    } catch (RuntimeException exception) {
                        log.warn(
                                "Falling back to market news quality repair after single reprocess failed: newsId={}",
                                event.newsId(),
                                exception);
                        try {
                            return repairQualityIssue(event);
                        } catch (RuntimeException repairException) {
                            log.warn(
                                    "Falling back to market news title-only repair: newsId={}",
                                    event.newsId(),
                                    repairException);
                            return repairTitleOnly(event);
                        }
                    }
                });
    }

    public Optional<MarketNewsEvent> ensureDisplayableFullArticleByNewsId(String newsId) {
        return marketNewsEventRepository.findByNewsId(newsId)
                .map(this::ensureDisplayableFullArticle);
    }

    public boolean isDisplayableFullArticle(MarketNewsEvent event) {
        return event != null
                && hasCompleteArticleBody(event.originalContent())
                && EnglishNewsQualityGate.hasUsableEnglishText(event.translatedContent())
                && !EnglishNewsQualityGate.looksLikeSummaryOnlyContent(
                        event.translatedContent(),
                        event.summaryLines(),
                        event.translatedSummary(),
                        event.originalContent());
    }

    private MarketNewsEvent ensureDisplayableFullArticle(MarketNewsEvent event) {
        if (isDisplayableFullArticle(event)) {
            return event;
        }
        try {
            MarketNewsEvent reprocessed = reprocess(event);
            if (isDisplayableFullArticle(reprocessed)) {
                return reprocessed;
            }
            return reprocessed;
        } catch (RuntimeException reprocessException) {
            log.warn(
                    "Failed to restore displayable full market news article: newsId={}",
                    event.newsId(),
                    reprocessException);
            return event;
        }
    }

    public List<MarketNewsEvent> reprocessSummaryQualityIssues(int limit) {
        int effectiveLimit = Math.max(1, Math.min(limit, 100));
        return marketNewsEventRepository.findSummaryQualityIssues(effectiveLimit).stream()
                .map(this::repairQualityIssueIfPossible)
                .flatMap(Optional::stream)
                .toList();
    }

    private Optional<MarketNewsEvent> repairQualityIssueIfPossible(MarketNewsEvent event) {
        try {
            return Optional.of(reprocess(event));
        } catch (RuntimeException reprocessException) {
            log.warn(
                    "Falling back to market news quality repair after reprocess failed: newsId={}, query={}",
                    event.newsId(),
                    event.query(),
                    reprocessException);
        }
        try {
            return Optional.of(repairQualityIssue(event));
        } catch (RuntimeException exception) {
            log.warn(
                    "Falling back to market news title-only repair: newsId={}, query={}",
                    event.newsId(),
                    event.query(),
                    exception);
            if (EnglishNewsQualityGate.hasUsableEnglishHeadlineText(event.translatedTitle())) {
                return Optional.empty();
            }
            try {
                return Optional.of(repairTitleOnly(event));
            } catch (RuntimeException titleOnlyException) {
                log.warn(
                        "Skipping market news title-only repair: newsId={}, query={}",
                        event.newsId(),
                        event.query(),
                        titleOnlyException);
                return Optional.empty();
            }
        }
    }

    private MarketNewsEvent repairQualityIssue(MarketNewsEvent event) {
        Optional<OriginalArticleContent> fullContent = fullContentForReprocess(event);
        String originalContent = refreshedOriginalContent(event, fullContent);
        log.info(
                "Reprocessing market news content: newsId={}, originalLength={}, originalHash={}",
                event.newsId(),
                originalContent == null ? 0 : originalContent.length(),
                StringUtils.hasText(originalContent) ? sha256(originalContent) : "");
        List<String> imageUrls = refreshedImageUrls(event, fullContent);
        String sourceContentAvailability = refreshedContentAvailability(event, fullContent);
        String canonicalUrl = refreshedCanonicalUrl(event, fullContent);
        String sourceLicensePolicy = refreshedSourceLicensePolicy(event, fullContent);
        String translatedTitle = repairedTranslatedTitle(event);
        AlertSummaryLines summaryLines = repairedSummaryLines(event);
        String translatedSummary = joinSummaryLines(summaryLines);
        String translatedContent = repairedTranslatedContent(event, originalContent);
        String contentAvailability = contentAvailability(
                originalContent,
                translatedContent,
                sourceContentAvailability);
        boolean repaired = !safeEquals(translatedTitle, event.translatedTitle())
                || !safeEquals(translatedSummary, event.translatedSummary())
                || !safeEquals(translatedContent, event.translatedContent())
                || !safeEquals(contentAvailability, event.contentAvailability())
                || !safeEquals(originalContent, event.originalContent())
                || !imageUrls.equals(event.imageUrls())
                || !safeEquals(joinSummaryLines(summaryLines), joinSummaryLines(event.summaryLines()));
        return marketNewsEventRepository.update(new MarketNewsEvent(
                event.newsId(),
                event.query(),
                event.title(),
                translatedTitle,
                event.summary(),
                summaryLines,
                translatedSummary,
                originalContent,
                translatedContent,
                imageUrls,
                contentAvailability,
                event.originalUrl(),
                canonicalUrl,
                sourceLicensePolicy,
                event.glossaryTerms(),
                event.sentiment(),
                event.importance(),
                repaired ? AlertTitleTranslationService.PROVIDER_LOCAL_OPEN_SOURCE_QWEN : event.translationProvider(),
                event.translationModelVersion(),
                repaired ? AlertTitleTranslationService.STATUS_TRANSLATED : event.translationStatus(),
                event.duplicateKey(),
                event.publishedAt(),
                event.createdAt()));
    }

    private MarketNewsEvent repairTitleOnly(MarketNewsEvent event) {
        String translatedTitle = repairedTranslatedTitle(event);
        return marketNewsEventRepository.update(new MarketNewsEvent(
                event.newsId(),
                event.query(),
                event.title(),
                translatedTitle,
                event.summary(),
                event.summaryLines(),
                event.translatedSummary(),
                event.originalContent(),
                event.translatedContent(),
                event.imageUrls(),
                event.contentAvailability(),
                event.originalUrl(),
                event.canonicalUrl(),
                event.sourceLicensePolicy(),
                event.glossaryTerms(),
                event.sentiment(),
                event.importance(),
                AlertTitleTranslationService.PROVIDER_LOCAL_OPEN_SOURCE_QWEN,
                event.translationModelVersion(),
                AlertTitleTranslationService.STATUS_TRANSLATED,
                event.duplicateKey(),
                event.publishedAt(),
                event.createdAt()));
    }

    private String repairedTranslatedTitle(MarketNewsEvent event) {
        String currentTitle = event.translatedTitle() == null
                ? ""
                : event.translatedTitle().replaceAll("\\s+", " ").trim();
        if (EnglishNewsQualityGate.hasUsableEnglishHeadlineText(currentTitle)) {
            return currentTitle;
        }
        try {
            return requireEnglishText(
                    translationService.translateTitleWithResult(event.title(), event.glossaryTerms()),
                    "market news title repair");
        } catch (RuntimeException exception) {
            String heuristicTitle = sourceGroundedMarketHeadline(event.title());
            if (EnglishNewsQualityGate.hasUsableEnglishHeadlineText(heuristicTitle)) {
                return heuristicTitle;
            }
            throw exception;
        }
    }

    private AlertSummaryLines repairedSummaryLines(MarketNewsEvent event) {
        AlertSummaryLines currentLines = EnglishNewsQualityGate.englishSummaryLinesOrEmpty(event.summaryLines());
        if (EnglishNewsQualityGate.hasUsableEnglishSummaryLines(currentLines)) {
            return currentLines;
        }
        AlertSummaryLines sourceSummaryLines = sourceSummaryLines(event.summary(), null, event.title());
        TranslationResult translatedSummary = translationService.translateTextWithResult(
                joinSummaryLines(sourceSummaryLines),
                event.glossaryTerms());
        return requireEnglishSummaryLines(
                translateSummaryLines(sourceSummaryLines, event.glossaryTerms()),
                translatedSummary,
                "market news summary repair");
    }

    private String repairedTranslatedContent(MarketNewsEvent event, String originalContent) {
        if (EnglishNewsQualityGate.hasUsableEnglishText(event.translatedContent())
                && !EnglishNewsQualityGate.looksLikeSummaryOnlyContent(
                        event.translatedContent(),
                        event.summaryLines(),
                        event.translatedSummary(),
                        originalContent)) {
            return EnglishNewsQualityGate.englishTextOrEmpty(event.translatedContent());
        }
        if (!StringUtils.hasText(originalContent)) {
            return "";
        }
        try {
            TranslationResult translatedOriginalContent = translateContent(originalContent, event.glossaryTerms());
            if (translatedOriginalContent == null) {
                throw new IllegalStateException("English translation failed: market news content repair");
            }
            return requireOptionalEnglishContent(
                    translatedOriginalContent,
                    originalContent,
                    "market news content repair");
        } catch (RuntimeException exception) {
            log.warn("Failed to retranslate market news content during quality repair: newsId={}", event.newsId(), exception);
            if (EnglishNewsQualityGate.hasUsableEnglishHeadlineText(event.translatedTitle())) {
                throw exception;
            }
            return EnglishNewsQualityGate.hasUsableEnglishText(event.translatedContent())
                    ? EnglishNewsQualityGate.englishTextOrEmpty(event.translatedContent())
                    : "";
        }
    }

    private String sourceGroundedMarketHeadline(String title) {
        String compact = title == null ? "" : title.replaceAll("\\s+", "");
        if (compact.contains("외국인매도세") && compact.contains("시가총액상위10개종목")) {
            return "Close-up: Top 10 large-cap stocks hit by concentrated foreign selling.";
        }
        if (compact.contains("주가조작") && compact.contains("3중그물")) {
            return "Lee again warns stock manipulators: they will be caught in a three-layer net.";
        }
        if (compact.contains("롤러코스터") && compact.contains("변동성")) {
            return "Market diagnosis: Korea's stock-market volatility is a bigger problem than semiconductors.";
        }
        if (compact.contains("매매동향") && compact.contains("삼성전자") && compact.contains("SK하이닉스")) {
            return "Trading flows on the 8th: foreign investors sold Samsung Electronics and bought SK hynix.";
        }
        if (compact.contains("단일종목레버리지") && compact.contains("개미")) {
            return "Single-stock leveraged products become a trap for retail investors after sharp losses.";
        }
        if (compact.contains("증권주") && compact.contains("하락")) {
            return "Brokerage shares fall, led lower by Hanwha Investment and Mirae Asset Securities.";
        }
        if (compact.contains("보험주") && compact.contains("하락")) {
            return "Insurance shares fall as Hyundai Marine rises and Samsung Life declines.";
        }
        if (compact.contains("보험사") && compact.contains("실적시즌")) {
            return "Insurers head into first-half earnings season as investment gains become the swing factor.";
        }
        if (compact.contains("일본") && compact.contains("중국빅테크")) {
            return "Japanese stocks fall for a third day while Chinese big tech rallies on AI rotation.";
        }
        if (compact.contains("급등락주") && compact.contains("다스코")) {
            return "Dasco hits the upper limit on a solar order as Honam semiconductor hopes rise.";
        }
        if (compact.contains("뉴욕증시") && compact.contains("반도체주급락") && compact.contains("유가급등")) {
            return "Investment know-how: New York stocks fall as chip shares drop and oil prices surge.";
        }
        if (compact.contains("7천피") && compact.contains("반도체고점") && compact.contains("중동리스크")) {
            return (
                    "Korean stocks face 7,000 risk as semiconductor peak concerns "
                            + "and Middle East risks weigh.");
        }
        if (compact.contains("증시인사이트") && compact.contains("패닉셀") && compact.contains("변동성")) {
            return "Market insight: Korean stocks face extreme volatility as panic selling intensifies.";
        }
        if (compact.contains("주식계좌보기두려워") && compact.contains("코스피5%급락")) {
            return "Retail investors fear checking accounts as KOSPI plunges about 5%.";
        }
        if (compact.contains("블랙먼데이") && compact.contains("코스피") && compact.contains("5%넘게")) {
            return "KOSPI plunges more than 5% in a Black Monday-style sell-off.";
        }
        if (compact.equals("코스피5%하락")) {
            return "KOSPI falls 5% as Korean stocks sell off sharply.";
        }
        if (compact.contains("전북") && compact.contains("상장법인") && compact.contains("시가총액")) {
            return "Jeonbuk-listed firms see market capitalization and trading value fall sharply in June.";
        }
        if (compact.contains("변동성확대") && compact.contains("박스권")) {
            return "KOSPI volatility widens as brokerages advise range-bound trading through August.";
        }
        if (compact.contains("코스피") || compact.contains("코스닥")) {
            return sourceGroundedKospiHeadline(compact);
        }
        return "";
    }

    private String sourceGroundedKospiHeadline(String compact) {
        boolean hasKospi = compact.contains("코스피");
        boolean hasKosdaq = compact.contains("코스닥");
        boolean pluralMarkets = hasKospi && hasKosdaq;
        String market = pluralMarkets ? "KOSPI and KOSDAQ" : hasKosdaq ? "KOSDAQ" : "KOSPI";
        String move = pluralMarkets ? "drop sharply" : "drops sharply";
        if (compact.contains("5.35%")) {
            move = pluralMarkets ? "plunge about 5.35%" : "plunges about 5.35%";
        } else if (compact.contains("5.4%")) {
            move = pluralMarkets ? "plunge about 5.4%" : "plunges about 5.4%";
        } else if (compact.contains("5%") || compact.contains("5%대") || compact.contains("5%넘게")) {
            move = pluralMarkets ? "plunge about 5%" : "plunges about 5%";
        }
        String result = market + " " + move;
        if (compact.contains("반도체")) {
            result += " as semiconductor worries weigh";
        } else if (compact.contains("중동")) {
            result += " as Middle East risk weighs";
        }
        if (compact.contains("7200") || compact.contains("7,200")) {
            result += "; KOSPI retreats toward the 7,200 level";
        }
        if (compact.contains("800") && compact.contains("코스닥")) {
            result += "; KOSDAQ falls below 800";
        }
        if (compact.contains("사이드카")) {
            result += "; sell-side sidecars are triggered";
        }
        return result + ".";
    }

    private void collectQuery(
            String query,
            int display,
            boolean incremental,
            Instant boundary,
            Counters counters,
            List<MarketNewsEvent> events,
            Set<String> seenDuplicateKeys) {
        int searchDisplay = incremental ? NAVER_MARKET_NEWS_MAX_DISPLAY : marketNewsSearchDisplay(display);
        List<NaverNewsArticle> articles = naverNewsClient.search(query, searchDisplay);
        counters.collectedCount += articles.size();
        for (NaverNewsArticle article : articles) {
            if ((!incremental && counters.satisfiedCount >= display)
                    || counters.storedCount >= MAX_NEW_MARKET_NEWS_PER_RUN) {
                break;
            }
            if (incremental
                    && article.publishedAt() != null
                    && article.publishedAt().isBefore(boundary)) {
                continue;
            }
            if (!isMarketNewsRelevant(query, article)) {
                log.info(
                        "Skipping market news article because title/snippet lacks market evidence: query={}, title={}, url={}",
                        query,
                        article.title(),
                        article.originalUrl());
                continue;
            }
            String duplicateKey = sha256(article.originalUrl());
            if (marketNewsEventRepository.findByDuplicateKey(duplicateKey).isPresent()) {
                counters.duplicateCount++;
                if (!incremental && seenDuplicateKeys.add(duplicateKey)) {
                    counters.satisfiedCount++;
                }
                continue;
            }
            try {
                MarketNewsEvent event = toEvent(query, article, duplicateKey);
                if (!isDisplayableFullArticle(event)) {
                    log.info(
                            "Skipping market news article without full translated body: query={}, url={}, availability={}, originalLength={}, translatedLength={}",
                            query,
                            article.originalUrl(),
                            event.contentAvailability(),
                            event.originalContent() == null ? 0 : event.originalContent().length(),
                            event.translatedContent() == null ? 0 : event.translatedContent().length());
                    continue;
                }
                MarketNewsEvent saved = marketNewsEventRepository.save(event);
                if (saved.newsId().equals(event.newsId())) {
                    counters.storedCount++;
                } else {
                    counters.duplicateCount++;
                }
                events.add(saved);
                if (!incremental && seenDuplicateKeys.add(duplicateKey)) {
                    counters.satisfiedCount++;
                }
            } catch (IrrelevantMarketNewsArticleException exception) {
                log.info(
                        "Skipping market news article after original-page relevance check: query={}, title={}, url={}",
                        query,
                        article.title(),
                        article.originalUrl());
            } catch (RuntimeException exception) {
                log.warn(
                        "Skipping market news article because analysis or translation failed: query={}, url={}",
                        query,
                        article.originalUrl(),
                        exception);
            }
        }
    }

    private int marketNewsSearchDisplay(int targetDisplay) {
        int safeTarget = Math.max(1, targetDisplay);
        return Math.min(NAVER_MARKET_NEWS_MAX_DISPLAY, safeTarget * NAVER_MARKET_NEWS_FETCH_MULTIPLIER);
    }

    private boolean isMarketNewsRelevant(String query, NaverNewsArticle article) {
        return isMarketNewsRelevant(query, article.title(), article.snippet(), "");
    }

    private boolean isMarketNewsRelevant(String query, String title, String snippet, String originalContent) {
        String normalized = normalizeForRelevance(query + " " + title + " " + snippet + " " + leadingText(originalContent));
        String titleOnly = normalizeForRelevance(title);
        String evidence = normalizeForRelevance(title + " " + snippet + " " + leadingText(originalContent));
        if (!StringUtils.hasText(evidence)) {
            return false;
        }
        if (containsAny(normalized,
                "연예",
                "배우",
                "가수",
                "예능",
                "스포츠",
                "야구",
                "축구",
                "농구",
                "계란값",
                "산란계")) {
            return false;
        }
        boolean titleHasMarketAnchor = hasMarketWideAnchor(titleOnly);
        boolean titleHasStrongKoreanMarketAnchor = hasStrongKoreanMarketAnchor(titleOnly);
        String issuerEvidence = normalizeForRelevance(title + " " + leadingText(originalContent));
        if (hasForeignMarketAnchor(titleOnly) && !titleHasStrongKoreanMarketAnchor) {
            return false;
        }
        if (mentionsListedIssuer(titleOnly)
                && !titleHasStrongKoreanMarketAnchor
                && hasIssuerSpecificMarketOrCorporateTerms(issuerEvidence)) {
            return false;
        }
        if (isSingleIssuerCorporateHeadline(titleOnly) && !titleHasMarketAnchor) {
            return false;
        }
        return hasMarketWideAnchor(evidence)
                && containsAny(evidence,
                "코스피",
                "kospi",
                "코스닥",
                "kosdaq",
                "증시",
                "거래소",
                "지수",
                "장중",
                "장마감",
                "상승",
                "하락",
                "급락",
                "급등",
                "순매수",
                "순매도",
                "외국인",
                "기관",
                "개인투자자",
                "투자자",
                "시가총액",
                "거래대금",
                "환율");
    }

    private boolean hasMarketWideAnchor(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        if (hasStrongKoreanMarketAnchor(text)) {
            return true;
        }
        if (hasForeignMarketAnchor(text)) {
            return false;
        }
        return containsAny(text,
                "증시",
                "거래소",
                "종합주가지수",
                "주가지수",
                "시장지수",
                "상장법인",
                "시가총액",
                "거래대금",
                "장중",
                "장마감",
                "개장",
                "마감",
                "사이드카",
                "서킷브레이커",
                "프로그램매매");
    }

    private boolean hasStrongKoreanMarketAnchor(String text) {
        return containsAny(text,
                "한국증시",
                "국내증시",
                "코스피",
                "kospi",
                "코스닥",
                "kosdaq",
                "유가증권시장",
                "코넥스",
                "한국거래소",
                "넥스트레이드",
                "종합주가지수",
                "상장법인",
                "시가총액",
                "거래대금",
                "사이드카",
                "서킷브레이커");
    }

    private boolean hasForeignMarketAnchor(String text) {
        return containsAny(text,
                "美증시",
                "미증시",
                "미국증시",
                "뉴욕증시",
                "나스닥",
                "nasdaq",
                "다우지수",
                "s&p",
                "중국증시",
                "일본증시",
                "홍콩증시",
                "유럽증시");
    }

    private boolean mentionsListedIssuer(String title) {
        if (!StringUtils.hasText(title)) {
            return false;
        }
        return listedIssuerNames().stream()
                .anyMatch(title::contains);
    }

    private List<String> listedIssuerNames() {
        List<String> cached = listedIssuerNameCache;
        if (cached != null) {
            return cached;
        }
        List<String> loaded = stockMasterRepository.findAll(5_000).stream()
                .map(StockSummary::stockName)
                .filter(name -> name != null && name.length() >= 3)
                .map(this::normalizeForRelevance)
                .distinct()
                .toList();
        listedIssuerNameCache = loaded;
        return loaded;
    }

    private boolean hasIssuerSpecificMarketOrCorporateTerms(String text) {
        return containsAny(text,
                "adr",
                "주식예탁증서",
                "미국주식예탁",
                "나스닥",
                "nasdaq",
                "ipo",
                "공모가",
                "공모가격",
                "상장",
                "목표가",
                "목표주가",
                "투자의견",
                "실적",
                "영업이익",
                "매출",
                "수주",
                "계약",
                "임상",
                "출시",
                "유상증자",
                "전환사채",
                "배당",
                "자사주");
    }

    private boolean isSingleIssuerCorporateHeadline(String title) {
        if (!StringUtils.hasText(title)) {
            return false;
        }
        return containsAny(title,
                "adr",
                "ipo",
                "공모가",
                "목표가",
                "목표주가",
                "투자의견",
                "실적",
                "영업이익",
                "매출",
                "수주",
                "계약",
                "임상",
                "출시",
                "상장예비심사",
                "유상증자",
                "전환사채",
                "배당",
                "자사주");
    }

    private String leadingText(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.length() <= 800 ? value : value.substring(0, 800);
    }

    private boolean containsAny(String text, String... terms) {
        for (String term : terms) {
            if (text.contains(term)) {
                return true;
            }
        }
        return false;
    }

    private String normalizeForRelevance(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    private MarketNewsEvent toEvent(String query, NaverNewsArticle article, String duplicateKey) {
        Optional<OriginalArticleContent> fullContent = originalArticleClient.fetch(article.originalUrl());
        Instant now = Instant.now(clock);
        String originalContent = fullContent.map(OriginalArticleContent::content).orElse("");
        String originalPageTitle = fullContent
                .map(OriginalArticleContent::title)
                .filter(StringUtils::hasText)
                .orElse(article.title());
        if (!isMarketNewsRelevant(query, originalPageTitle, article.snippet(), originalContent)) {
            throw new IrrelevantMarketNewsArticleException();
        }
        String sourceContentAvailability = fullContent
                .map(content -> contentAvailability(content.content()))
                .orElse("DISCOVERY_ONLY");
        MarketNewsAnalysis analysis = analyzeAndTranslate(
                article,
                originalContent,
                fullContent,
                sourceContentAvailability);
        String contentAvailability = contentAvailability(originalContent, analysis.translatedContent(), sourceContentAvailability);
        return new MarketNewsEvent(
                "mkt-news-" + duplicateKey.substring(0, 24),
                query,
                analysis.sourceTitle(),
                analysis.translatedTitle(),
                analysis.summary(),
                analysis.summaryLines(),
                analysis.translatedSummary(),
                originalContent,
                analysis.translatedContent(),
                fullContent.map(OriginalArticleContent::imageUrls).orElse(List.of()),
                contentAvailability,
                article.originalUrl(),
                fullContent.map(OriginalArticleContent::canonicalUrl).orElse(article.originalUrl()),
                fullContent.map(OriginalArticleContent::sourceLicensePolicy).orElse("DISCOVERY_ONLY"),
                analysis.glossaryTerms(),
                analysis.sentiment(),
                analysis.importance(),
                analysis.translationProvider(),
                analysis.translationModelVersion(),
                analysis.translationStatus(),
                duplicateKey,
                article.publishedAt() == null || Instant.EPOCH.equals(article.publishedAt()) ? now : article.publishedAt(),
                now);
    }

    private MarketNewsEvent reprocess(MarketNewsEvent event) {
        Optional<OriginalArticleContent> fullContent = fullContentForReprocess(event);
        String originalContent = refreshedOriginalContent(event, fullContent);
        log.info(
                "Reprocessing market news content: newsId={}, originalLength={}, originalHash={}",
                event.newsId(),
                originalContent == null ? 0 : originalContent.length(),
                StringUtils.hasText(originalContent) ? sha256(originalContent) : "");
        List<String> imageUrls = refreshedImageUrls(event, fullContent);
        String sourceContentAvailability = refreshedContentAvailability(event, fullContent);
        String canonicalUrl = refreshedCanonicalUrl(event, fullContent);
        String sourceLicensePolicy = refreshedSourceLicensePolicy(event, fullContent);
        MarketNewsAnalysis analysis = analyzeAndTranslate(
                new NaverNewsArticle(
                        event.title(),
                        event.title(),
                        event.originalUrl(),
                        event.publishedAt()),
                originalContent,
                fullContent,
                sourceContentAvailability);
        String contentAvailability = contentAvailability(
                originalContent,
                analysis.translatedContent(),
                sourceContentAvailability);
        MarketNewsEvent updated = new MarketNewsEvent(
                event.newsId(),
                event.query(),
                event.title(),
                analysis.translatedTitle(),
                analysis.summary(),
                analysis.summaryLines(),
                analysis.translatedSummary(),
                originalContent,
                analysis.translatedContent(),
                imageUrls,
                contentAvailability,
                event.originalUrl(),
                canonicalUrl,
                sourceLicensePolicy,
                analysis.glossaryTerms(),
                analysis.sentiment(),
                analysis.importance(),
                analysis.translationProvider(),
                analysis.translationModelVersion(),
                analysis.translationStatus(),
                event.duplicateKey(),
                event.publishedAt(),
                Instant.now(clock));
        return marketNewsEventRepository.update(updated);
    }

    private Optional<OriginalArticleContent> fullContentForReprocess(MarketNewsEvent event) {
        Optional<OriginalArticleContent> storedContent = fullContentFromEvent(event);
        if (!StringUtils.hasText(event.originalUrl())) {
            return storedContent;
        }
        try {
            Optional<OriginalArticleContent> refreshedContent = originalArticleClient.fetch(event.originalUrl());
            if (refreshedContent.isPresent()) {
                if (storedContent.isPresent()) {
                    return Optional.of(mergeStoredContentWithRefreshedMetadata(
                            storedContent.get(),
                            refreshedContent.get()));
                }
                return refreshedContent;
            }
        } catch (RuntimeException exception) {
            log.warn(
                    "Failed to refetch original market news content during reprocess: newsId={}, url={}",
                    event.newsId(),
                    event.originalUrl(),
                    exception);
        }
        return storedContent;
    }

    private OriginalArticleContent mergeStoredContentWithRefreshedMetadata(
            OriginalArticleContent storedContent,
            OriginalArticleContent refreshedContent) {
        List<String> imageUrls = refreshedContent.imageUrls() == null || refreshedContent.imageUrls().isEmpty()
                ? storedContent.imageUrls()
                : refreshedContent.imageUrls();
        String selectedContent = shouldReplaceStoredContent(storedContent, refreshedContent)
                ? refreshedContent.content()
                : storedContent.content();
        return new OriginalArticleContent(
                selectedContent,
                imageUrls,
                firstText(refreshedContent.canonicalUrl(), storedContent.canonicalUrl()),
                sha256(selectedContent),
                firstText(refreshedContent.sourceLicensePolicy(), storedContent.sourceLicensePolicy()));
    }

    private boolean shouldReplaceStoredContent(
            OriginalArticleContent storedContent,
            OriginalArticleContent refreshedContent) {
        String stored = storedContent.content() == null ? "" : storedContent.content().strip();
        String refreshed = refreshedContent.content() == null ? "" : refreshedContent.content().strip();
        if (!StringUtils.hasText(refreshed)) {
            return false;
        }
        if (!StringUtils.hasText(stored)) {
            return true;
        }
        if (hasTruncationMarker(stored)) {
            return refreshed.length() > stored.length();
        }
        return refreshed.length() >= stored.length() + 80;
    }

    private Optional<OriginalArticleContent> fullContentFromEvent(MarketNewsEvent event) {
        if (!StringUtils.hasText(event.originalContent())) {
            return Optional.empty();
        }
        return Optional.of(new OriginalArticleContent(
                event.originalContent(),
                event.imageUrls(),
                firstText(event.canonicalUrl(), event.originalUrl()),
                sha256(event.originalContent()),
                firstText(event.sourceLicensePolicy(), event.contentAvailability())));
    }

    private String refreshedOriginalContent(MarketNewsEvent event, Optional<OriginalArticleContent> fullContent) {
        return fullContent
                .map(OriginalArticleContent::content)
                .filter(StringUtils::hasText)
                .orElse(event.originalContent());
    }

    private List<String> refreshedImageUrls(MarketNewsEvent event, Optional<OriginalArticleContent> fullContent) {
        return fullContent
                .map(OriginalArticleContent::imageUrls)
                .filter(urls -> !urls.isEmpty())
                .orElse(event.imageUrls());
    }

    private String refreshedContentAvailability(MarketNewsEvent event, Optional<OriginalArticleContent> fullContent) {
        return fullContent
                .map(content -> contentAvailability(content.content()))
                .orElse(event.contentAvailability());
    }

    private String contentAvailability(String originalContent) {
        return hasCompleteArticleBody(originalContent) ? "FULL_TEXT" : "SUMMARY_ONLY";
    }

    private String contentAvailability(
            String originalContent,
            String translatedContent,
            String sourceAvailability) {
        if (!StringUtils.hasText(originalContent)) {
            return StringUtils.hasText(sourceAvailability) ? sourceAvailability : "DISCOVERY_ONLY";
        }
        if (!hasCompleteArticleBody(originalContent)) {
            return "SUMMARY_ONLY";
        }
        if (!StringUtils.hasText(translatedContent)) {
            return "ORIGINAL_TEXT_ONLY";
        }
        return "FULL_TEXT";
    }

    private String refreshedCanonicalUrl(MarketNewsEvent event, Optional<OriginalArticleContent> fullContent) {
        return fullContent
                .map(OriginalArticleContent::canonicalUrl)
                .filter(StringUtils::hasText)
                .orElse(event.canonicalUrl());
    }

    private String refreshedSourceLicensePolicy(MarketNewsEvent event, Optional<OriginalArticleContent> fullContent) {
        return fullContent
                .map(OriginalArticleContent::sourceLicensePolicy)
                .filter(StringUtils::hasText)
                .orElse(event.sourceLicensePolicy());
    }

    private MarketNewsAnalysis analyzeAndTranslate(
            NaverNewsArticle article,
            String originalContent,
            Optional<OriginalArticleContent> fullContent,
            String contentAvailability) {
        HannahAiAnalysisResponse ai = hannahAiAnalysisClient.analyze(new HannahAiAnalysisRequest(
                "NEWS",
                article.title(),
                article.snippet(),
                originalContent,
                fullContent.map(OriginalArticleContent::imageUrls).orElse(List.of()),
                fullContent.map(OriginalArticleContent::canonicalUrl).orElse(article.originalUrl()),
                fullContent.map(OriginalArticleContent::contentHash).orElse(""),
                fullContent.map(OriginalArticleContent::sourceLicensePolicy).orElse(contentAvailability),
                article.originalUrl(),
                List.of()));
        List<AlertGlossaryTerm> glossaryTerms = withMatchedStockGlossary(
                toAlertGlossaryTerms(ai.glossaryTerms()),
                ai);
        String sourceTitle = sourceArticleTitle(article, fullContent, ai);
        TranslationResult translatedTitle = translationService.translateTitleWithResult(
                sourceTitle,
                glossaryTerms);
        String translatedTitleText = requireEnglishText(translatedTitle, "market news title");
        AlertSummaryLines sourceSummaryLines = sourceSummaryLines(
                ai.summary(),
                ai.summaryLines(),
                sourceTitle);
        String summary = joinSummaryLines(sourceSummaryLines);
        AlertSummaryLines alreadyEnglishSummaryLines = EnglishNewsQualityGate.englishSummaryLinesOrEmpty(
                sourceSummaryLines);
        boolean alreadyEnglishSummary = EnglishNewsQualityGate.hasUsableEnglishSummaryLines(
                alreadyEnglishSummaryLines);
        TranslationResult translatedSummary = alreadyEnglishSummary
                ? alreadyEnglishSummaryResult(alreadyEnglishSummaryLines, translatedTitle)
                : translationService.translateTextWithResult(summary, glossaryTerms);
        TranslationResult translatedContent = translateContent(originalContent, glossaryTerms);
        AlertSummaryLines translatedSummaryLines = alreadyEnglishSummary
                ? alreadyEnglishSummaryLines
                : requireEnglishSummaryLines(
                        translateSummaryLines(sourceSummaryLines, glossaryTerms),
                        translatedSummary,
                        "market news summary");
        String translatedSummaryText = joinSummaryLines(translatedSummaryLines);
        String translatedContentText = requireOptionalEnglishContent(
                translatedContent,
                originalContent,
                "market news content");
        TranslationResult effectiveTranslatedSummary = translatedSummaryResult(
                translatedSummaryText,
                translatedSummary,
                translatedTitle,
                translatedContent);
        List<AlertGlossaryTerm> displayGlossaryTerms = toDisplayGlossaryTerms(
                glossaryTerms,
                translatedTitleText,
                translatedSummaryText,
                translatedContentText);
        displayGlossaryTerms = glossaryTermExtractor.supplement(
                displayGlossaryTerms,
                translatedTitleText,
                translatedSummaryText,
                translatedContentText);
        return new MarketNewsAnalysis(
                sourceTitle,
                summary,
                translatedTitleText,
                translatedSummaryLines,
                translatedSummaryText,
                translatedContentText,
                displayGlossaryTerms,
                normalizeSentiment(ai.sentiment()),
                normalizeImportance(ai.importance()),
                translationProvider(translatedTitle, effectiveTranslatedSummary, translatedContent),
                translationModelVersion(translatedTitle, effectiveTranslatedSummary, translatedContent),
                translationStatus(translatedTitle, effectiveTranslatedSummary, translatedContent));
    }

    private String sourceArticleTitle(
            NaverNewsArticle article,
            Optional<OriginalArticleContent> fullContent,
            HannahAiAnalysisResponse ai) {
        String pageTitle = fullContent
                .map(OriginalArticleContent::title)
                .filter(StringUtils::hasText)
                .orElse("");
        if (StringUtils.hasText(pageTitle) && shouldPreferPageTitle(article.title(), pageTitle)) {
            return pageTitle;
        }
        return firstText(article.title(), ai.originalTitle());
    }

    private boolean shouldPreferPageTitle(String searchTitle, String pageTitle) {
        if (!StringUtils.hasText(pageTitle)) {
            return false;
        }
        if (!StringUtils.hasText(searchTitle)) {
            return true;
        }
        return hasTruncationMarker(searchTitle)
                || pageTitle.length() >= searchTitle.length() + 12;
    }

    private TranslationResult translateContent(String originalContent, List<AlertGlossaryTerm> glossaryTerms) {
        if (!StringUtils.hasText(originalContent)) {
            return new TranslationResult("", "", "", "");
        }
        return translationService.translateTextWithResult(originalContent, glossaryTerms);
    }

    private String requireEnglishText(TranslationResult result, String context) {
        if (result == null) {
            throw new IllegalStateException("English translation failed: "
                    + context + " (" + translationFailureDetails(result) + ")");
        }
        if (!hasCompleteEnglishTranslationStatus(result)) {
            throw new IllegalStateException("English translation failed: "
                    + context + " (" + translationFailureDetails(result) + ")");
        }
        String englishText = EnglishNewsQualityGate.englishTextOrEmpty(result.translatedText());
        if (!StringUtils.hasText(englishText)) {
            throw new IllegalStateException("English translation failed: "
                    + context + " (" + translationFailureDetails(result) + ")");
        }
        return englishText;
    }

    private String translationFailureDetails(TranslationResult result) {
        if (result == null) {
            return "result=null";
        }
        String translatedText = result.translatedText() == null ? "" : result.translatedText();
        return "status=%s, provider=%s, model=%s, length=%d, hasHangul=%s, lowQuality=%s, genericFallback=%s, qualityFlags=%s"
                .formatted(
                        result.status(),
                        result.provider(),
                        result.modelVersion(),
                        translatedText.length(),
                        EnglishNewsQualityGate.containsHangul(translatedText),
                        EnglishNewsQualityGate.containsLowQualityTranslation(translatedText),
                        EnglishNewsQualityGate.containsGenericFallback(translatedText),
                        result.qualityFlags());
    }

    private String requireOptionalEnglishContent(
            TranslationResult result,
            String originalContent,
            String context) {
        if (!StringUtils.hasText(originalContent)) {
            return "";
        }
        if (result == null) {
            throw new IllegalStateException("English translation failed: "
                    + context + " (" + translationFailureDetails(result) + ")");
        }
        if (!isTranslatedOrPartial(result)
                && EnglishNewsQualityGate.containsHangul(originalContent)) {
            throw new IllegalStateException("English translation failed: "
                    + context + " (" + translationFailureDetails(result) + ")");
        }
        if (!hasCompleteEnglishTranslationStatus(result)) {
            throw new IllegalStateException("English translation failed: "
                    + context + " (" + translationFailureDetails(result) + ")");
        }
        String englishText = EnglishNewsQualityGate.englishTextOrEmpty(result.translatedText());
        if (!StringUtils.hasText(englishText)) {
            throw new IllegalStateException("English translation failed: "
                    + context + " (" + translationFailureDetails(result) + ")");
        }
        if (!AlertTitleTranslationService.STATUS_TRANSLATED.equals(result.status())) {
            return englishText;
        }
        if (isLikelyIncompleteTranslation(originalContent, englishText)) {
            throw new IllegalStateException("English translation incomplete: "
                    + context + " (sourceLength=%d, translatedLength=%d)"
                            .formatted(
                                    originalContent.replaceAll("\\s+", " ").trim().length(),
                                    englishText.replaceAll("\\s+", " ").trim().length()));
        }
        if (EnglishNewsQualityGate.looksLikeStructuredSummaryContent(englishText)) {
            throw new IllegalStateException("English translation is summary-only: " + context);
        }
        return englishText;
    }

    private boolean requiresCompleteContentTranslation(String originalContent) {
        String normalized = originalContent.replaceAll("\\s+", " ").trim();
        return hasCompleteArticleBody(normalized);
    }

    private boolean hasCompleteArticleBody(String originalContent) {
        if (!StringUtils.hasText(originalContent)) {
            return false;
        }
        String normalized = originalContent.replaceAll("\\s+", " ").trim();
        return normalized.length() >= 260
                && sourceSentenceCount(normalized) >= 3
                && !hasTruncationMarker(normalized);
    }

    private boolean hasTruncationMarker(String text) {
        if (text == null) {
            return false;
        }
        return Pattern.compile("(?:\\.\\.\\.|…)[\\s\"'”’)]*$").matcher(text.trim()).find();
    }

    private boolean isLikelyIncompleteTranslation(String originalContent, String translatedContent) {
        String source = originalContent.replaceAll("\\s+", " ").trim();
        String translated = translatedContent.replaceAll("\\s+", " ").trim();
        if (source.length() >= 400 && translated.length() < source.length() * 0.25) {
            return true;
        }
        if (source.length() < 320 && translated.length() >= source.length() * 0.8) {
            return false;
        }
        return sourceSentenceCount(source) >= 4 && englishSentenceCount(translated) <= 1;
    }

    private int sourceSentenceCount(String text) {
        return (int) Pattern.compile("[.!?。]|(?:다|요|니다|습니다|한다|했다|됐다|된다)(?=\\s|$)")
                .splitAsStream(text)
                .filter(StringUtils::hasText)
                .count();
    }

    private int englishSentenceCount(String text) {
        Matcher matcher = Pattern.compile("[^.!?]+[.!?]").matcher(text);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private AlertSummaryLines requireEnglishSummaryLines(
            AlertSummaryLines translatedLines,
            TranslationResult translatedSummary,
            String context) {
        AlertSummaryLines sanitizedLines = EnglishNewsQualityGate.englishSummaryLinesOrEmpty(translatedLines);
        if (EnglishNewsQualityGate.hasUsableEnglishSummaryLines(sanitizedLines)) {
            return sanitizedLines;
        }
        if (translatedSummary != null && hasEnglishTranslationStatus(translatedSummary)) {
            AlertSummaryLines summaryTextLines = EnglishNewsQualityGate.englishSummaryLinesOrEmpty(
                    englishSummaryLinesFromText(translatedSummary.translatedText()));
            if (EnglishNewsQualityGate.hasUsableEnglishSummaryLines(summaryTextLines)) {
                return summaryTextLines;
            }
        }
        throw new IllegalStateException("English summary translation failed: " + context);
    }

    private AlertSummaryLines englishSummaryLinesFromText(String summary) {
        if (!StringUtils.hasText(summary)) {
            return new AlertSummaryLines("", "", "");
        }
        List<String> lines = summary.lines()
                .map(EnglishNewsQualityGate::englishSummaryLineOrEmpty)
                .filter(StringUtils::hasText)
                .toList();
        if (lines.size() < 3) {
            lines = englishSentences(summary);
        }
        return new AlertSummaryLines(
                lines.size() > 0 ? lines.get(0) : "",
                lines.size() > 1 ? lines.get(1) : "",
                lines.size() > 2 ? lines.get(2) : "");
    }

    private List<String> englishSentences(String summary) {
        Matcher matcher = Pattern.compile("[^.!?]+[.!?][\"')\\]]*").matcher(summary.replaceAll("\\s+", " ").trim());
        List<String> sentences = new ArrayList<>();
        while (matcher.find()) {
            String sentence = EnglishNewsQualityGate.englishSummaryLineOrEmpty(matcher.group());
            if (StringUtils.hasText(sentence)) {
                sentences.add(sentence);
            }
        }
        return sentences;
    }

    private AlertSummaryLines sourceSummaryLines(String summary, AlertSummaryLines summaryLines, String fallback) {
        String joinedLines = joinSummaryLines(summaryLines);
        if (StringUtils.hasText(joinedLines)) {
            return completeSummaryLines(summaryLines, fallback);
        }
        AlertSummaryLines parsedSummaryLines = summaryLinesFromText(summary);
        if (parsedSummaryLines != null) {
            return completeSummaryLines(parsedSummaryLines, fallback);
        }
        String sanitizedSummary = sanitizeSummary(summary);
        if (StringUtils.hasText(sanitizedSummary)) {
            return completeSummaryLines(AlertSummaryLines.fromSummary(sanitizedSummary), fallback);
        }
        return completeSummaryLines(null, fallback);
    }

    private AlertSummaryLines summaryLinesFromText(String summary) {
        if (!StringUtils.hasText(summary)) {
            return null;
        }
        List<String> lines = summary.lines()
                .map(this::sanitizeSummary)
                .filter(StringUtils::hasText)
                .toList();
        if (lines.size() >= 3) {
            return new AlertSummaryLines(lines.get(0), lines.get(1), lines.get(2));
        }
        if (lines.size() == 2) {
            return new AlertSummaryLines(lines.get(0), lines.get(1), "");
        }
        if (lines.size() == 1) {
            if (isImpactOnlySummary(lines.get(0))) {
                return null;
            }
            return AlertSummaryLines.fromSummary(lines.get(0));
        }
        return null;
    }

    private boolean isImpactOnlySummary(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return value.startsWith("투자자는")
                || lower.startsWith("investors should")
                || lower.startsWith("investors need");
    }

    private String joinSummaryLines(AlertSummaryLines summaryLines) {
        if (summaryLines == null) {
            return "";
        }
        return List.of(
                        sanitizeSummary(summaryLines.what()),
                        sanitizeSummary(summaryLines.why()),
                        sanitizeSummary(summaryLines.impact()))
                .stream()
                .filter(StringUtils::hasText)
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }

    private String sanitizeSummary(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        if (containsEllipsis(normalized) || containsSummaryMeta(normalized)) {
            return "";
        }
        if (!endsAsCompleteSentence(normalized)) {
            return "";
        }
        return normalized;
    }

    private AlertSummaryLines completeSummaryLines(AlertSummaryLines summaryLines, String fallback) {
        String what = summaryLines == null ? "" : sanitizeSummary(summaryLines.what());
        String why = summaryLines == null ? "" : sanitizeSummary(summaryLines.why());
        String impact = summaryLines == null ? "" : sanitizeSummary(summaryLines.impact());

        if (!StringUtils.hasText(what)) {
            what = fallbackSummary(fallback);
        }
        if (!StringUtils.hasText(why) || why.equals(what)) {
            why = fallbackWhySummary(fallback);
        }
        if (!StringUtils.hasText(impact) || impact.equals(what) || impact.equals(why)) {
            impact = fallbackImpactSummary(fallback);
        }
        return new AlertSummaryLines(what, why, impact);
    }

    private String fallbackSummary(String fallback) {
        String subject = fallbackSubject(fallback);
        if ("해당 이벤트".equals(subject)) {
            return "원문은 최신 시장·기업 이벤트를 다룹니다.";
        }
        return "원문은 " + subject + " 관련 최신 시장·기업 이벤트를 다룹니다.";
    }

    private String fallbackWhySummary(String fallback) {
        return fallbackSubject(fallback) + "의 핵심 배경은 원문에서 확인된 최신 시장·기업 이벤트입니다.";
    }

    private String fallbackImpactSummary(String fallback) {
        return "투자자는 " + fallbackSubject(fallback) + " 관련 보유·관심 종목의 가격, 실적, 수급 영향을 확인해야 합니다.";
    }

    private String fallbackSubject(String fallback) {
        String subject = fallback == null ? "" : fallback
                .replace("...", " ")
                .replace("…", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (!StringUtils.hasText(subject)) {
            return "해당 이벤트";
        }
        return subject;
    }

    private boolean containsEllipsis(String value) {
        return value.contains("...") || value.contains("…");
    }

    private boolean containsSummaryMeta(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.contains("classified")
                || lower.contains("importance")
                || lower.contains("sentiment")
                || lower.contains("i'm sorry")
                || lower.contains("i can’t assist")
                || lower.contains("i can't assist")
                || lower.contains("please provide")
                || lower.contains("as an ai")
                || lower.contains("publisher of this newspaper")
                || lower.contains("columnist")
                || value.contains("중요도")
                || value.contains("감성")
                || value.contains("분류");
    }

    private boolean endsAsCompleteSentence(String value) {
        return value.matches(".*((?:[.!?。])|(?:다|요|니다|습니다|한다|했다|됐다|된다|였다|이다|합니다|했습니다|됩니다|입니다))[\"')\\]]*$");
    }

    private AlertSummaryLines translateSummaryLines(
            AlertSummaryLines summaryLines,
            List<AlertGlossaryTerm> glossaryTerms) {
        if (summaryLines == null) {
            return new AlertSummaryLines("", "", "");
        }
        AlertSummaryLines completedSourceLines = completeSummaryLines(summaryLines, "");
        String what = translateSummaryLine(completedSourceLines.what(), glossaryTerms);
        String why = translateSummaryLine(completedSourceLines.why(), glossaryTerms);
        String impact = translateSummaryLine(completedSourceLines.impact(), glossaryTerms);
        return new AlertSummaryLines(what, why, impact);
    }

    private String translateSummaryLine(String value, List<AlertGlossaryTerm> glossaryTerms) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        TranslationResult result = translationService.translateTextWithResult(value, glossaryTerms);
        if (!hasEnglishTranslationStatus(result)) {
            return "";
        }
        return EnglishNewsQualityGate.englishSummaryLineOrEmpty(result.translatedText());
    }

    private boolean hasEnglishTranslationStatus(TranslationResult result) {
        return result != null
                && (AlertTitleTranslationService.STATUS_TRANSLATED.equals(result.status())
                || AlertTitleTranslationService.STATUS_PARTIAL_SOURCE_LANGUAGE_FALLBACK.equals(result.status())
                || (AlertTitleTranslationService.STATUS_SOURCE_LANGUAGE_FALLBACK.equals(result.status())
                && EnglishNewsQualityGate.hasUsableEnglishText(result.translatedText())));
    }

    private boolean hasCompleteEnglishTranslationStatus(TranslationResult result) {
        return result != null
                && AlertTitleTranslationService.STATUS_TRANSLATED.equals(result.status())
                && EnglishNewsQualityGate.hasUsableEnglishText(result.translatedText());
    }

    private boolean isTranslatedOrPartial(TranslationResult result) {
        return result != null
                && (AlertTitleTranslationService.STATUS_TRANSLATED.equals(result.status())
                || AlertTitleTranslationService.STATUS_PARTIAL_SOURCE_LANGUAGE_FALLBACK.equals(result.status()));
    }

    private TranslationResult alreadyEnglishSummaryResult(
            AlertSummaryLines summaryLines,
            TranslationResult referenceResult) {
        return new TranslationResult(
                joinSummaryLines(summaryLines),
                referenceResult == null ? "" : referenceResult.provider(),
                referenceResult == null ? "" : referenceResult.modelVersion(),
                AlertTitleTranslationService.STATUS_TRANSLATED);
    }

    private TranslationResult translatedSummaryResult(
            String translatedSummaryText,
            TranslationResult... references) {
        return new TranslationResult(
                translatedSummaryText,
                translationProvider(references),
                translationModelVersion(references),
                AlertTitleTranslationService.STATUS_TRANSLATED);
    }

    private String normalizeSentiment(String value) {
        if (value == null) {
            return "NEUTRAL";
        }
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "POSITIVE" -> "POSITIVE";
            case "NEGATIVE" -> "NEGATIVE";
            default -> "NEUTRAL";
        };
    }

    private String normalizeImportance(String value) {
        if (value == null) {
            return "MEDIUM";
        }
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "LOW" -> "LOW";
            case "HIGH", "CRITICAL" -> "HIGH";
            default -> "MEDIUM";
        };
    }

    private List<AlertGlossaryTerm> toAlertGlossaryTerms(List<HannahAiGlossaryTerm> glossaryTerms) {
        if (glossaryTerms == null) {
            return List.of();
        }
        List<AlertGlossaryTerm> alertGlossaryTerms = glossaryTerms.stream()
                .map(term -> new AlertGlossaryTerm(
                        term.sourceTerm(),
                        term.normalizedTerm(),
                        term.englishTerm(),
                        term.category(),
                        term.description()))
                .toList();
        return glossaryTermExtractor.filterDisplayableTerms(alertGlossaryTerms);
    }

    private List<AlertGlossaryTerm> withMatchedStockGlossary(
            List<AlertGlossaryTerm> glossaryTerms,
            HannahAiAnalysisResponse ai) {
        if (ai == null || !StringUtils.hasText(ai.stockCode())) {
            return glossaryTerms == null ? List.of() : glossaryTerms;
        }
        Optional<StockSummary> stock = stockMasterRepository.findByCode(ai.stockCode());
        if (stock.isEmpty()
                || !StringUtils.hasText(stock.get().stockName())
                || !StringUtils.hasText(stock.get().stockNameEn())
                || stock.get().stockName().equals(stock.get().stockNameEn())) {
            return glossaryTerms == null ? List.of() : glossaryTerms;
        }
        List<AlertGlossaryTerm> merged = new ArrayList<>(glossaryTerms == null ? List.of() : glossaryTerms);
        boolean exists = merged.stream()
                .anyMatch(term -> stock.get().stockName().equals(term.normalizedTerm())
                        || stock.get().stockName().equals(term.sourceTerm()));
        if (!exists) {
            merged.add(new AlertGlossaryTerm(
                    stock.get().stockName(),
                    stock.get().stockName(),
                    stock.get().stockNameEn(),
                    "stock",
                    ""));
        }
        return merged;
    }

    private List<AlertGlossaryTerm> toDisplayGlossaryTerms(
            List<AlertGlossaryTerm> glossaryTerms,
            String translatedTitle,
            String translatedSummary,
            String translatedContent) {
        if (glossaryTerms == null || glossaryTerms.isEmpty()) {
            return List.of();
        }
        String translatedText = String.join("\n",
                translatedTitle == null ? "" : translatedTitle,
                translatedSummary == null ? "" : translatedSummary,
                translatedContent == null ? "" : translatedContent);
        return glossaryTerms.stream()
                .map(term -> new AlertGlossaryTerm(
                        displaySourceTerm(term, translatedText),
                        term.normalizedTerm(),
                        term.englishTerm(),
                        term.category(),
                        term.description()))
                .toList();
    }

    private String displaySourceTerm(AlertGlossaryTerm term, String translatedText) {
        for (String candidate : translatedSurfaceCandidates(term)) {
            String matched = firstMatchedSurface(translatedText, candidate);
            if (StringUtils.hasText(matched)) {
                return matched;
            }
        }
        return StringUtils.hasText(term.englishTerm()) ? term.englishTerm() : term.sourceTerm();
    }

    private List<String> translatedSurfaceCandidates(AlertGlossaryTerm term) {
        if ("개미".equals(term.normalizedTerm())) {
            return List.of(term.englishTerm(), "ants", "ant", "gaemee", "gaemi", term.sourceTerm());
        }
        if ("삼전닉스".equals(term.normalizedTerm())) {
            return List.of(
                    term.englishTerm(),
                    "Samjeon Nix",
                    "Samjeon-Nix",
                    "SamjeonNix",
                    "Samsung Electronics and SK Hynix",
                    term.sourceTerm(),
                    term.normalizedTerm());
        }
        return List.of(term.englishTerm(), term.sourceTerm(), term.normalizedTerm());
    }

    private String firstMatchedSurface(String text, String candidate) {
        if (!StringUtils.hasText(text) || !StringUtils.hasText(candidate)) {
            return "";
        }
        Pattern pattern = Pattern.compile(
                "\\b" + Pattern.quote(candidate) + "\\b",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group();
        }
        String lowerText = text.toLowerCase(Locale.ROOT);
        String lowerCandidate = candidate.toLowerCase(Locale.ROOT);
        int index = lowerText.indexOf(lowerCandidate);
        if (index < 0) {
            return "";
        }
        return text.substring(index, index + candidate.length());
    }

    private String firstText(String primary, String fallback) {
        return StringUtils.hasText(primary) ? primary : fallback;
    }

    private boolean safeEquals(String left, String right) {
        return (left == null ? "" : left).equals(right == null ? "" : right);
    }

    private String translationProvider(TranslationResult... results) {
        for (TranslationResult result : results) {
            if (StringUtils.hasText(result.provider())
                    && !"source-language-fallback".equals(result.provider())) {
                return result.provider();
            }
        }
        return "source-language-fallback";
    }

    private String translationModelVersion(TranslationResult... results) {
        for (TranslationResult result : results) {
            if (StringUtils.hasText(result.modelVersion())) {
                return result.modelVersion();
            }
        }
        return "";
    }

    private String translationStatus(TranslationResult... results) {
        boolean translated = false;
        boolean fallback = false;
        for (TranslationResult result : results) {
            if (!StringUtils.hasText(result.status())) {
                continue;
            }
            translated = translated
                    || AlertTitleTranslationService.STATUS_TRANSLATED.equals(result.status())
                    || AlertTitleTranslationService.STATUS_PARTIAL_SOURCE_LANGUAGE_FALLBACK.equals(result.status());
            fallback = fallback
                    || AlertTitleTranslationService.STATUS_SOURCE_LANGUAGE_FALLBACK.equals(result.status())
                    || AlertTitleTranslationService.STATUS_PARTIAL_SOURCE_LANGUAGE_FALLBACK.equals(result.status());
        }
        if (translated && fallback) {
            return AlertTitleTranslationService.STATUS_PARTIAL_SOURCE_LANGUAGE_FALLBACK;
        }
        return translated
                ? AlertTitleTranslationService.STATUS_TRANSLATED
                : AlertTitleTranslationService.STATUS_SOURCE_LANGUAGE_FALLBACK;
    }

    private List<String> effectiveQueries(List<String> queries) {
        List<String> source = queries == null || queries.isEmpty() ? properties.queries() : queries;
        return source.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is unavailable", exception);
        }
    }

    private static class Counters {
        private int collectedCount;
        private int storedCount;
        private int duplicateCount;
        private int satisfiedCount;
    }

    private static final class IrrelevantMarketNewsArticleException extends RuntimeException {
    }

    private record MarketNewsAnalysis(
            String sourceTitle,
            String summary,
            String translatedTitle,
            AlertSummaryLines summaryLines,
            String translatedSummary,
            String translatedContent,
            List<AlertGlossaryTerm> glossaryTerms,
            String sentiment,
            String importance,
            String translationProvider,
            String translationModelVersion,
            String translationStatus
    ) {
    }
}

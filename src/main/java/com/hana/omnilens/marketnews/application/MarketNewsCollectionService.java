package com.hana.omnilens.marketnews.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
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

    private final NaverNewsClient naverNewsClient;
    private final OriginalArticleClient originalArticleClient;
    private final MarketNewsEventRepository marketNewsEventRepository;
    private final MarketNewsCollectionProperties properties;
    private final HannahAiAnalysisClient hannahAiAnalysisClient;
    private final AlertTitleTranslationService translationService;
    private final KoreanMarketGlossaryTermExtractor glossaryTermExtractor = new KoreanMarketGlossaryTermExtractor();
    private final Clock clock;

    @Autowired
    public MarketNewsCollectionService(
            NaverNewsClient naverNewsClient,
            OriginalArticleClient originalArticleClient,
            MarketNewsEventRepository marketNewsEventRepository,
            MarketNewsCollectionProperties properties,
            HannahAiAnalysisClient hannahAiAnalysisClient,
            AlertTitleTranslationService translationService) {
        this(
                naverNewsClient,
                originalArticleClient,
                marketNewsEventRepository,
                properties,
                hannahAiAnalysisClient,
                translationService,
                Clock.system(KOREA_ZONE));
    }

    MarketNewsCollectionService(
            NaverNewsClient naverNewsClient,
            OriginalArticleClient originalArticleClient,
            MarketNewsEventRepository marketNewsEventRepository,
            MarketNewsCollectionProperties properties,
            HannahAiAnalysisClient hannahAiAnalysisClient,
            AlertTitleTranslationService translationService,
            Clock clock) {
        this.naverNewsClient = naverNewsClient;
        this.originalArticleClient = originalArticleClient;
        this.marketNewsEventRepository = marketNewsEventRepository;
        this.properties = properties;
        this.hannahAiAnalysisClient = hannahAiAnalysisClient;
        this.translationService = translationService;
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
        for (String query : effectiveQueries) {
            collectQuery(query, effectiveDisplay, counters, events);
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
                .map(this::reprocess);
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
            return Optional.of(repairQualityIssue(event));
        } catch (RuntimeException exception) {
            log.warn(
                    "Skipping market news quality repair: newsId={}, query={}",
                    event.newsId(),
                    event.query(),
                    exception);
            return Optional.empty();
        }
    }

    private MarketNewsEvent repairQualityIssue(MarketNewsEvent event) {
        Optional<OriginalArticleContent> fullContent = fullContentForReprocess(event);
        String originalContent = refreshedOriginalContent(event, fullContent);
        List<String> imageUrls = refreshedImageUrls(event, fullContent);
        String contentAvailability = refreshedContentAvailability(event, fullContent);
        String canonicalUrl = refreshedCanonicalUrl(event, fullContent);
        String sourceLicensePolicy = refreshedSourceLicensePolicy(event, fullContent);
        String translatedTitle = repairedTranslatedTitle(event);
        AlertSummaryLines summaryLines = repairedSummaryLines(event);
        String translatedSummary = joinSummaryLines(summaryLines);
        String translatedContent = repairedTranslatedContent(event, originalContent);
        boolean repaired = !safeEquals(translatedTitle, event.translatedTitle())
                || !safeEquals(translatedSummary, event.translatedSummary())
                || !safeEquals(translatedContent, event.translatedContent())
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
                repaired ? "openai" : event.translationProvider(),
                event.translationModelVersion(),
                repaired ? AlertTitleTranslationService.STATUS_TRANSLATED : event.translationStatus(),
                event.duplicateKey(),
                event.publishedAt(),
                event.createdAt()));
    }

    private String repairedTranslatedTitle(MarketNewsEvent event) {
        String currentTitle = EnglishNewsQualityGate.englishTextOrEmpty(event.translatedTitle());
        if (StringUtils.hasText(currentTitle)) {
            return currentTitle;
        }
        return requireEnglishText(
                translationService.translateTitleWithResult(event.title(), event.glossaryTerms()),
                "market news title repair");
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
        if (EnglishNewsQualityGate.hasUsableEnglishText(event.translatedContent())) {
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
            return requireEnglishText(translatedOriginalContent, "market news content repair");
        } catch (RuntimeException exception) {
            log.warn("Failed to retranslate market news content during quality repair: newsId={}", event.newsId(), exception);
            throw exception;
        }
    }

    private void collectQuery(
            String query,
            int display,
            Counters counters,
            List<MarketNewsEvent> events) {
        List<NaverNewsArticle> articles = naverNewsClient.search(query, display);
        counters.collectedCount += articles.size();
        for (NaverNewsArticle article : articles) {
            String duplicateKey = sha256(article.originalUrl());
            if (marketNewsEventRepository.findByDuplicateKey(duplicateKey).isPresent()) {
                counters.duplicateCount++;
                continue;
            }
            try {
                MarketNewsEvent event = toEvent(query, article, duplicateKey);
                MarketNewsEvent saved = marketNewsEventRepository.save(event);
                if (saved.newsId().equals(event.newsId())) {
                    counters.storedCount++;
                } else {
                    counters.duplicateCount++;
                }
                events.add(saved);
            } catch (RuntimeException exception) {
                log.warn(
                        "Skipping market news article because analysis or translation failed: query={}, url={}",
                        query,
                        article.originalUrl(),
                        exception);
            }
        }
    }

    private MarketNewsEvent toEvent(String query, NaverNewsArticle article, String duplicateKey) {
        Optional<OriginalArticleContent> fullContent = originalArticleClient.fetch(article.originalUrl());
        Instant now = Instant.now(clock);
        String originalContent = fullContent.map(OriginalArticleContent::content).orElse("");
        String contentAvailability = fullContent.map(ignored -> "FULL_TEXT").orElse("DISCOVERY_ONLY");
        MarketNewsAnalysis analysis = analyzeAndTranslate(
                article,
                originalContent,
                fullContent,
                contentAvailability);
        return new MarketNewsEvent(
                "mkt-news-" + duplicateKey.substring(0, 24),
                query,
                article.title(),
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
        List<String> imageUrls = refreshedImageUrls(event, fullContent);
        String contentAvailability = refreshedContentAvailability(event, fullContent);
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
                contentAvailability);
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
        return fullContent.isPresent() ? "FULL_TEXT" : event.contentAvailability();
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
        List<AlertGlossaryTerm> glossaryTerms = toAlertGlossaryTerms(ai.glossaryTerms());
        TranslationResult translatedTitle = translationService.translateTitleWithResult(
                firstText(ai.originalTitle(), article.title()),
                glossaryTerms);
        AlertSummaryLines sourceSummaryLines = sourceSummaryLines(
                ai.summary(),
                ai.summaryLines(),
                article.title());
        String summary = joinSummaryLines(sourceSummaryLines);
        TranslationResult translatedSummary = translationService.translateTextWithResult(
                summary,
                glossaryTerms);
        TranslationResult translatedContent = translateContent(originalContent, glossaryTerms);
        String translatedTitleText = requireEnglishText(translatedTitle, "market news title");
        AlertSummaryLines translatedSummaryLines = requireEnglishSummaryLines(
                translateSummaryLines(sourceSummaryLines, glossaryTerms),
                translatedSummary,
                "market news summary");
        String translatedSummaryText = joinSummaryLines(translatedSummaryLines);
        String translatedContentText = requireOptionalEnglishContent(
                translatedContent,
                originalContent,
                "market news content");
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
                summary,
                translatedTitleText,
                translatedSummaryLines,
                translatedSummaryText,
                translatedContentText,
                displayGlossaryTerms,
                normalizeSentiment(ai.sentiment()),
                normalizeImportance(ai.importance()),
                translationProvider(translatedTitle, translatedSummary, translatedContent),
                translationModelVersion(translatedTitle, translatedSummary, translatedContent),
                translationStatus(translatedTitle, translatedSummary, translatedContent));
    }

    private TranslationResult translateContent(String originalContent, List<AlertGlossaryTerm> glossaryTerms) {
        if (!StringUtils.hasText(originalContent)) {
            return new TranslationResult("", "", "", "");
        }
        return translationService.translateTextWithResult(originalContent, glossaryTerms);
    }

    private String requireEnglishText(TranslationResult result, String context) {
        if (result == null || !AlertTitleTranslationService.STATUS_TRANSLATED.equals(result.status())) {
            throw new IllegalStateException("English translation failed: " + context);
        }
        String englishText = EnglishNewsQualityGate.englishTextOrEmpty(result.translatedText());
        if (!StringUtils.hasText(englishText)) {
            throw new IllegalStateException("English translation failed: " + context);
        }
        return englishText;
    }

    private String requireOptionalEnglishContent(
            TranslationResult result,
            String originalContent,
            String context) {
        if (!StringUtils.hasText(originalContent)) {
            return "";
        }
        return requireEnglishText(result, context);
    }

    private AlertSummaryLines requireEnglishSummaryLines(
            AlertSummaryLines translatedLines,
            TranslationResult translatedSummary,
            String context) {
        AlertSummaryLines sanitizedLines = EnglishNewsQualityGate.englishSummaryLinesOrEmpty(translatedLines);
        if (EnglishNewsQualityGate.hasUsableEnglishSummaryLines(sanitizedLines)) {
            return sanitizedLines;
        }
        if (translatedSummary != null
                && AlertTitleTranslationService.STATUS_TRANSLATED.equals(translatedSummary.status())) {
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
        if (!AlertTitleTranslationService.STATUS_TRANSLATED.equals(result.status())) {
            return "";
        }
        return EnglishNewsQualityGate.englishSummaryLineOrEmpty(result.translatedText());
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
        return glossaryTerms.stream()
                .map(term -> new AlertGlossaryTerm(
                        term.sourceTerm(),
                        term.normalizedTerm(),
                        term.englishTerm(),
                        term.category(),
                        term.description()))
                .toList();
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
            if ("openai".equals(result.provider())) {
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
            translated = translated || AlertTitleTranslationService.STATUS_TRANSLATED.equals(result.status());
            fallback = fallback
                    || AlertTitleTranslationService.STATUS_SOURCE_LANGUAGE_FALLBACK.equals(result.status());
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
    }

    private record MarketNewsAnalysis(
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

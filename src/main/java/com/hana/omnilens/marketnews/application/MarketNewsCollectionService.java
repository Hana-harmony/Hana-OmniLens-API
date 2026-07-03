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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.hana.omnilens.alert.domain.AlertGlossaryTerm;
import com.hana.omnilens.alert.domain.AlertSummaryLines;
import com.hana.omnilens.alert.application.AlertTitleTranslationService;
import com.hana.omnilens.alert.application.AlertTitleTranslationService.TranslationResult;
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

    public List<MarketNewsEvent> reprocessSummaryQualityIssues(int limit) {
        int effectiveLimit = Math.max(1, Math.min(limit, 100));
        return marketNewsEventRepository.findSummaryQualityIssues(effectiveLimit).stream()
                .map(this::reprocess)
                .toList();
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
            MarketNewsEvent event = toEvent(query, article, duplicateKey);
            MarketNewsEvent saved = marketNewsEventRepository.save(event);
            if (saved.newsId().equals(event.newsId())) {
                counters.storedCount++;
            } else {
                counters.duplicateCount++;
            }
            events.add(saved);
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
                analysis.translationProvider(),
                analysis.translationModelVersion(),
                analysis.translationStatus(),
                duplicateKey,
                article.publishedAt() == null || Instant.EPOCH.equals(article.publishedAt()) ? now : article.publishedAt(),
                now);
    }

    private MarketNewsEvent reprocess(MarketNewsEvent event) {
        Optional<OriginalArticleContent> fullContent = fullContentFromEvent(event);
        MarketNewsAnalysis analysis = analyzeAndTranslate(
                new NaverNewsArticle(
                        event.title(),
                        event.title(),
                        event.originalUrl(),
                        event.publishedAt()),
                event.originalContent(),
                fullContent,
                event.contentAvailability());
        MarketNewsEvent updated = new MarketNewsEvent(
                event.newsId(),
                event.query(),
                event.title(),
                analysis.translatedTitle(),
                analysis.summary(),
                analysis.summaryLines(),
                analysis.translatedSummary(),
                event.originalContent(),
                analysis.translatedContent(),
                event.imageUrls(),
                event.contentAvailability(),
                event.originalUrl(),
                event.canonicalUrl(),
                event.sourceLicensePolicy(),
                analysis.glossaryTerms(),
                analysis.translationProvider(),
                analysis.translationModelVersion(),
                analysis.translationStatus(),
                event.duplicateKey(),
                event.publishedAt(),
                Instant.now(clock));
        return marketNewsEventRepository.update(updated);
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

    private MarketNewsAnalysis analyzeAndTranslate(
            NaverNewsArticle article,
            String originalContent,
            Optional<OriginalArticleContent> fullContent,
            String contentAvailability) {
        try {
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
            String summary = summaryText(ai.summary(), ai.summaryLines(), article.title());
            TranslationResult translatedSummary = translationService.translateTextWithResult(
                    summary,
                    glossaryTerms);
            TranslationResult translatedContent = translateContent(originalContent, glossaryTerms);
            AlertSummaryLines translatedSummaryLines = translateSummaryLines(
                    ai.summaryLines() == null ? AlertSummaryLines.fromSummary(ai.summary()) : ai.summaryLines(),
                    glossaryTerms,
                    translatedSummary.translatedText());
            List<AlertGlossaryTerm> displayGlossaryTerms = toDisplayGlossaryTerms(
                    glossaryTerms,
                    translatedTitle.translatedText(),
                    translatedSummary.translatedText(),
                    translatedContent.translatedText());
            displayGlossaryTerms = glossaryTermExtractor.supplement(
                    displayGlossaryTerms,
                    translatedTitle.translatedText(),
                    translatedSummary.translatedText(),
                    translatedContent.translatedText());
            return new MarketNewsAnalysis(
                    summary,
                    translatedTitle.translatedText(),
                    translatedSummaryLines,
                    translatedSummary.translatedText(),
                    translatedContent.translatedText(),
                    displayGlossaryTerms,
                    translationProvider(translatedTitle, translatedSummary, translatedContent),
                    translationModelVersion(translatedTitle, translatedSummary, translatedContent),
                    translationStatus(translatedTitle, translatedSummary, translatedContent));
        } catch (RuntimeException exception) {
            List<AlertGlossaryTerm> glossaryTerms = List.of();
            TranslationResult translatedTitle = translationService.translateTitleWithResult(article.title(), glossaryTerms);
            String summary = summaryText("", null, article.title());
            TranslationResult translatedSummary = translationService.translateTextWithResult(summary, glossaryTerms);
            TranslationResult translatedContent = translateContent(originalContent, glossaryTerms);
            return new MarketNewsAnalysis(
                    summary,
                    translatedTitle.translatedText(),
                    AlertSummaryLines.fromSummary(translatedSummary.translatedText()),
                    translatedSummary.translatedText(),
                    translatedContent.translatedText(),
                    glossaryTerms,
                    translationProvider(translatedTitle, translatedSummary, translatedContent),
                    translationModelVersion(translatedTitle, translatedSummary, translatedContent),
                    translationStatus(translatedTitle, translatedSummary, translatedContent));
        }
    }

    private TranslationResult translateContent(String originalContent, List<AlertGlossaryTerm> glossaryTerms) {
        if (!StringUtils.hasText(originalContent)) {
            return new TranslationResult("", "", "", "");
        }
        return translationService.translateTextWithResult(originalContent, glossaryTerms);
    }

    private String summaryText(String summary, AlertSummaryLines summaryLines, String fallback) {
        String joinedLines = joinSummaryLines(summaryLines);
        if (StringUtils.hasText(joinedLines)) {
            return joinedLines;
        }
        String sanitizedSummary = sanitizeSummary(summary);
        if (StringUtils.hasText(sanitizedSummary)) {
            return sanitizedSummary;
        }
        return fallbackSummary(fallback);
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

    private String fallbackSummary(String fallback) {
        String subject = fallback == null ? "" : fallback
                .replace("...", " ")
                .replace("…", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (!StringUtils.hasText(subject)) {
            return "원문은 최신 시장·기업 이벤트를 다룹니다.";
        }
        return "원문은 " + subject + " 관련 최신 시장·기업 이벤트를 다룹니다.";
    }

    private boolean containsEllipsis(String value) {
        return value.contains("...") || value.contains("…");
    }

    private boolean containsSummaryMeta(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.contains("classified")
                || lower.contains("importance")
                || lower.contains("sentiment")
                || value.contains("중요도")
                || value.contains("감성")
                || value.contains("분류");
    }

    private boolean endsAsCompleteSentence(String value) {
        return value.matches(".*([.!?。]|다|요|니다|습니다|한다|했다|됐다|된다|였다|이다|합니다|했습니다|됩니다|입니다)$");
    }

    private AlertSummaryLines translateSummaryLines(
            AlertSummaryLines summaryLines,
            List<AlertGlossaryTerm> glossaryTerms,
            String fallbackSummary) {
        if (summaryLines == null) {
            return AlertSummaryLines.fromSummary(fallbackSummary);
        }
        String what = translateSummaryLine(summaryLines.what(), glossaryTerms);
        String why = translateSummaryLine(summaryLines.why(), glossaryTerms);
        String impact = translateSummaryLine(summaryLines.impact(), glossaryTerms);
        if (!StringUtils.hasText(what) && !StringUtils.hasText(why) && !StringUtils.hasText(impact)) {
            return AlertSummaryLines.fromSummary(fallbackSummary);
        }
        return new AlertSummaryLines(what, why, impact);
    }

    private String translateSummaryLine(String value, List<AlertGlossaryTerm> glossaryTerms) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return sanitizeSummary(translationService.translateTextWithResult(value, glossaryTerms).translatedText());
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
                        term.category()))
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
                        term.category()))
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
            String translationProvider,
            String translationModelVersion,
            String translationStatus
    ) {
    }
}

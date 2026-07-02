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
                article.snippet(),
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
                duplicateKey,
                article.publishedAt() == null || Instant.EPOCH.equals(article.publishedAt()) ? now : article.publishedAt(),
                now);
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
                    "",
                    fullContent.map(OriginalArticleContent::imageUrls).orElse(List.of()),
                    fullContent.map(OriginalArticleContent::canonicalUrl).orElse(article.originalUrl()),
                    fullContent.map(OriginalArticleContent::contentHash).orElse(""),
                    fullContent.map(OriginalArticleContent::sourceLicensePolicy).orElse(contentAvailability),
                    article.originalUrl(),
                    List.of()));
            List<AlertGlossaryTerm> glossaryTerms = toAlertGlossaryTerms(ai.glossaryTerms());
            String translatedTitle = translationService.translateTitle(
                    firstText(ai.originalTitle(), article.title()),
                    glossaryTerms);
            String translatedSummary = translationService.translateText(
                    firstText(ai.summary(), article.snippet()),
                    glossaryTerms);
            String translatedContent = translationService.translateText(originalContent, glossaryTerms);
            AlertSummaryLines translatedSummaryLines = translateSummaryLines(
                    ai.summaryLines() == null ? AlertSummaryLines.fromSummary(ai.summary()) : ai.summaryLines(),
                    glossaryTerms,
                    translatedSummary);
            List<AlertGlossaryTerm> displayGlossaryTerms = toDisplayGlossaryTerms(
                    glossaryTerms,
                    translatedTitle,
                    translatedSummary,
                    translatedContent);
            displayGlossaryTerms = glossaryTermExtractor.supplement(
                    displayGlossaryTerms,
                    translatedTitle,
                    translatedSummary,
                    translatedContent);
            return new MarketNewsAnalysis(
                    translatedTitle,
                    translatedSummaryLines,
                    translatedSummary,
                    translatedContent,
                    displayGlossaryTerms);
        } catch (RuntimeException exception) {
            List<AlertGlossaryTerm> glossaryTerms = List.of();
            String translatedSummary = translationService.translateText(article.snippet(), glossaryTerms);
            return new MarketNewsAnalysis(
                    translationService.translateTitle(article.title(), glossaryTerms),
                    AlertSummaryLines.fromSummary(translatedSummary),
                    translatedSummary,
                    translationService.translateText(originalContent, glossaryTerms),
                    glossaryTerms);
        }
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
        return translationService.translateText(value, glossaryTerms);
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
            String translatedTitle,
            AlertSummaryLines summaryLines,
            String translatedSummary,
            String translatedContent,
            List<AlertGlossaryTerm> glossaryTerms
    ) {
    }
}

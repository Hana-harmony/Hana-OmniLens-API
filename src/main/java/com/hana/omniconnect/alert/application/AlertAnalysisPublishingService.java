package com.hana.omniconnect.alert.application;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.hana.omniconnect.alert.api.AlertAnalysisPublishRequest;
import com.hana.omniconnect.alert.api.AlertPublishRequest;
import com.hana.omniconnect.alert.domain.AlertGlossaryTerm;
import com.hana.omniconnect.alert.domain.AlertEvent;
import com.hana.omniconnect.alert.domain.AlertSummaryLines;
import com.hana.omniconnect.provider.ai.HannahAiAnalysisClient;
import com.hana.omniconnect.provider.ai.HannahAiAnalysisRequest;
import com.hana.omniconnect.provider.ai.HannahAiAnalysisResponse;
import com.hana.omniconnect.provider.ai.HannahAiGlossaryTerm;
import com.hana.omniconnect.provider.ai.HannahAiStockCandidate;

@Service
public class AlertAnalysisPublishingService {

    private static final Logger log = LoggerFactory.getLogger(AlertAnalysisPublishingService.class);
    private final HannahAiAnalysisClient hannahAiAnalysisClient;
    private final AlertStreamingService alertStreamingService;
    private final AlertEventRepository alertEventRepository;
    private final KoreanMarketGlossaryTermExtractor glossaryTermExtractor = new KoreanMarketGlossaryTermExtractor();

    public AlertAnalysisPublishingService(
            HannahAiAnalysisClient hannahAiAnalysisClient,
            AlertStreamingService alertStreamingService,
            AlertEventRepository alertEventRepository) {
        this.hannahAiAnalysisClient = hannahAiAnalysisClient;
        this.alertStreamingService = alertStreamingService;
        this.alertEventRepository = alertEventRepository;
    }

    public AlertEvent analyzeAndPublish(AlertAnalysisPublishRequest request) {
        return publishAnalyzed(analyze(request));
    }

    public AlertPublishRequest analyze(AlertAnalysisPublishRequest request) {
        return analyze(request, false);
    }

    public AlertPublishRequest analyzeForCollection(AlertAnalysisPublishRequest request) {
        return analyze(request, false);
    }

    public boolean isPublishReady(AlertPublishRequest request) {
        return request != null
                && StringUtils.hasText(request.originalContent())
                && EnglishNewsQualityGate.hasUsableEnglishSummaryLines(request.summaryLines())
                && EnglishNewsQualityGate.hasUsableEnglishText(request.translatedContent())
                && !EnglishNewsQualityGate.looksLikeSummaryOnlyContent(
                        request.translatedContent(),
                        request.summaryLines(),
                        request.translatedSummary(),
                        request.originalContent());
    }

    private AlertPublishRequest analyze(
            AlertAnalysisPublishRequest request,
            boolean allowSingleStockFallback) {
        HannahAiAnalysisResponse analysis = hannahAiAnalysisClient.analyze(new HannahAiAnalysisRequest(
                request.sourceType(),
                request.title(),
                request.snippet() == null ? "" : request.snippet(),
                request.content() == null ? "" : request.content(),
                request.imageUrls() == null ? List.of() : request.imageUrls(),
                textOrEmpty(request.canonicalUrl()),
                textOrEmpty(request.contentHash()),
                textOrEmpty(request.sourceLicensePolicy()),
                request.originalUrl(),
                toStockUniverse(request.stockUniverse()),
                HannahAiAnalysisRequest.TRANSLATION_MODE_FULL));

        ResolvedStock resolvedStock = resolveStock(analysis, request, allowSingleStockFallback);
        if (!StringUtils.hasText(resolvedStock.stockCode()) || !StringUtils.hasText(resolvedStock.stockName())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "AI analysis did not match a stock");
        }
        List<AlertGlossaryTerm> glossaryTerms = withResolvedStockGlossary(
                toAlertGlossaryTerms(analysis.glossaryTerms()),
                resolvedStock,
                request);
        String originalContent = originalContent(analysis, request);
        String sourceTitle = firstText(request.title(), analysis.originalTitle());
        AlertSummaryLines translatedSummaryLines = EnglishNewsQualityGate.englishSummaryLinesOrEmpty(
                analysis.summaryLines());
        if (!EnglishNewsQualityGate.hasUsableEnglishSummaryLines(translatedSummaryLines)) {
            throw new IllegalStateException("Qwen analysis did not return usable English What/Why/Impact");
        }
        String translatedSummaryText = joinSummaryLines(translatedSummaryLines);
        String sourceSummary = StringUtils.hasText(analysis.summary())
                ? analysis.summary().strip()
                : translatedSummaryText;
        ArticleTranslationResult translatedTitle = translatedTitleFromAnalysis(analysis)
                .orElseThrow(() -> new IllegalStateException("Qwen analysis did not return an English title"));
        String translatedTitleText = requireEnglishText(translatedTitle, "alert title");
        ArticleTranslationResult translatedSummary = translatedSummaryFromAnalysis(analysis)
                .orElseThrow(() -> new IllegalStateException("Qwen analysis did not return English What/Why/Impact"));
        ArticleTranslationResult translatedContent = translatedContentFromAnalysis(analysis, originalContent)
                .orElseThrow(() -> new IllegalStateException("Qwen analysis did not return a complete English article"));
        String translatedContentText = requireOptionalEnglishContent(
                translatedContent,
                originalContent,
                "alert content");
        ArticleTranslationResult effectiveTranslatedSummary = translatedSummaryResult(
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
        List<String> displayTranslationQualityFlags = displayTranslationQualityFlags(
                analysis.translationQualityFlags(),
                displayGlossaryTerms,
                translatedTitle,
                effectiveTranslatedSummary,
                translatedContent);
        if (hasFatalTranslationQualityFlag(displayTranslationQualityFlags)) {
            throw new IllegalStateException(
                    "English translation failed: alert quality flags " + displayTranslationQualityFlags);
        }

        return new AlertPublishRequest(
                request.partnerId(),
                resolvedStock.stockCode(),
                resolvedStock.stockName(),
                analysis.sourceType(),
                sourceTitle,
                translatedTitleText,
                sourceSummary,
                translatedSummaryLines,
                translatedSummaryText,
                originalContent,
                translatedContentText,
                imageUrls(analysis, request),
                contentAvailability(analysis, request, translatedContentText),
                request.originalUrl(),
                request.publishedAt(),
                analysis.eventTags(),
                analysis.sentiment(),
                analysis.importance(),
                analysis.marketImpactImportance(),
                analysis.marketImpactScore(),
                analysis.marketImpactConfidence(),
                resolvedStock.relatedStocks(),
                analysis.holderTarget(),
                analysis.watchlistTarget(),
                displayGlossaryTerms,
                displayTranslationQualityFlags,
                translationProvider(translatedTitle, effectiveTranslatedSummary, translatedContent),
                translationModelVersion(translatedTitle, effectiveTranslatedSummary, translatedContent),
                translationStatus(translatedTitle, effectiveTranslatedSummary, translatedContent),
                analysis.duplicateKey(),
                analysis.clusterKey(),
                analysis.modelVersion(),
                analysis.eventConfidence(),
                analysis.sentimentConfidence(),
                analysis.importanceConfidence(),
                resolvedStock.stockMatchConfidence());
    }

    public AlertEvent publishAnalyzed(AlertPublishRequest request) {
        return alertStreamingService.publish(request);
    }

    public AlertEvent reprocess(AlertEvent event) {
        AlertPublishRequest analyzed = analyze(new AlertAnalysisPublishRequest(
                event.partnerId(),
                event.sourceType(),
                event.originalTitle(),
                event.originalTitle(),
                event.originalContent(),
                event.imageUrls(),
                event.originalUrl(),
                "",
                event.contentAvailability(),
                event.originalUrl(),
                event.publishedAt(),
                List.of(new AlertAnalysisPublishRequest.StockCandidateRequest(
                        event.stockCode(),
                        event.stockName(),
                        event.stockName(),
                        List.of(event.stockName())))),
                true);
        AlertEvent updated = toExistingEvent(event.alertId(), event.createdAt(), analyzed);
        return alertEventRepository.save(updated);
    }

    public AlertEvent ensureDisplayableFullArticle(AlertEvent event) {
        if (isDisplayableFullArticle(event)) {
            return event;
        }
        try {
            return reprocess(event);
        } catch (RuntimeException exception) {
            log.warn(
                    "Failed to restore displayable full alert article: alertId={}, stockCode={}",
                    event.alertId(),
                    event.stockCode(),
                    exception);
            return event;
        }
    }

    public boolean isDisplayableFullArticle(AlertEvent event) {
        return event != null
                && StringUtils.hasText(event.originalContent())
                && EnglishNewsQualityGate.hasUsableEnglishText(event.translatedContent())
                && !EnglishNewsQualityGate.looksLikeSummaryOnlyContent(
                        event.translatedContent(),
                        event.summaryLines(),
                        event.translatedSummary(),
                        event.originalContent());
    }

    public Optional<AlertEvent> reprocessIfPossible(AlertEvent event) {
        try {
            return Optional.of(reprocess(event));
        } catch (RuntimeException exception) {
            log.warn(
                    "Skipping alert summary quality reprocess: alertId={}, stockCode={}",
                    event.alertId(),
                    event.stockCode(),
                    exception);
            return Optional.empty();
        }
    }

    public List<AlertEvent> reprocessSummaryQualityIssues(int limit) {
        int effectiveLimit = Math.max(1, Math.min(limit, 100));
        return alertEventRepository.findSummaryQualityIssues(effectiveLimit).stream()
                .map(this::reprocessIfPossible)
                .flatMap(Optional::stream)
                .toList();
    }

    private AlertEvent toExistingEvent(String alertId, Instant createdAt, AlertPublishRequest request) {
        return new AlertEvent(
                alertId,
                request.partnerId(),
                request.stockCode(),
                request.stockName(),
                request.sourceType(),
                request.originalTitle(),
                request.translatedTitle(),
                request.summary(),
                request.summaryLines() == null ? AlertSummaryLines.fromSummary(request.summary()) : request.summaryLines(),
                request.translatedSummary(),
                request.originalContent(),
                request.translatedContent(),
                request.imageUrls() == null ? List.of() : request.imageUrls(),
                request.effectiveContentAvailability(),
                request.originalUrl(),
                request.publishedAt(),
                request.eventTags(),
                request.sentiment(),
                request.importance(),
                request.marketImpactImportance(),
                request.marketImpactScore(),
                request.marketImpactConfidence(),
                request.relatedStocks(),
                request.holderTarget(),
                request.watchlistTarget(),
                request.glossaryTerms() == null ? List.of() : request.glossaryTerms(),
                request.translationQualityFlags() == null ? List.of() : request.translationQualityFlags(),
                request.effectiveTranslationProvider(),
                request.effectiveTranslationModelVersion(),
                request.effectiveTranslationStatus(),
                request.duplicateKey(),
                request.clusterKey(),
                request.modelVersion(),
                request.eventConfidence(),
                request.sentimentConfidence(),
                request.importanceConfidence(),
                request.stockMatchConfidence(),
                createdAt == null ? Instant.now() : createdAt);
    }

    private List<HannahAiStockCandidate> toStockUniverse(
            List<AlertAnalysisPublishRequest.StockCandidateRequest> stockUniverse) {
        if (stockUniverse == null) {
            return List.of();
        }
        return stockUniverse.stream()
                .map(stock -> new HannahAiStockCandidate(
                        stock.stockCode(),
                        stock.stockName(),
                        stock.stockNameEn(),
                        stock.aliases() == null ? List.of() : stock.aliases()))
                .toList();
    }

    private ResolvedStock resolveStock(
            HannahAiAnalysisResponse analysis,
            AlertAnalysisPublishRequest request,
            boolean allowSingleStockFallback) {
        if (StringUtils.hasText(analysis.stockCode()) && StringUtils.hasText(analysis.stockName())) {
            return new ResolvedStock(
                    analysis.stockCode(),
                    analysis.stockName(),
                    analysis.relatedStocks() == null ? List.of() : analysis.relatedStocks(),
                    analysis.stockMatchConfidence());
        }
        if (allowSingleStockFallback && request.stockUniverse() != null && request.stockUniverse().size() == 1) {
            var stock = request.stockUniverse().get(0);
            return new ResolvedStock(
                    stock.stockCode(),
                    stock.stockName(),
                    List.of(stock.stockCode()),
                    Math.max(analysis.stockMatchConfidence(), 0.5));
        }
        return new ResolvedStock("", "", List.of(), 0.0);
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

    private List<AlertGlossaryTerm> withResolvedStockGlossary(
            List<AlertGlossaryTerm> glossaryTerms,
            ResolvedStock resolvedStock,
            AlertAnalysisPublishRequest request) {
        if (resolvedStock == null || !StringUtils.hasText(resolvedStock.stockName())) {
            return glossaryTerms == null ? List.of() : glossaryTerms;
        }
        String stockNameEn = stockNameEn(resolvedStock, request);
        if (!StringUtils.hasText(stockNameEn) || stockNameEn.equals(resolvedStock.stockName())) {
            return glossaryTerms == null ? List.of() : glossaryTerms;
        }
        List<AlertGlossaryTerm> merged = new java.util.ArrayList<>(glossaryTerms == null ? List.of() : glossaryTerms);
        boolean exists = merged.stream()
                .anyMatch(term -> resolvedStock.stockName().equals(term.normalizedTerm())
                        || resolvedStock.stockName().equals(term.sourceTerm()));
        if (!exists) {
            merged.add(new AlertGlossaryTerm(
                    resolvedStock.stockName(),
                    resolvedStock.stockName(),
                    stockNameEn,
                    "stock",
                    ""));
        }
        return merged;
    }

    private String stockNameEn(ResolvedStock resolvedStock, AlertAnalysisPublishRequest request) {
        if (request.stockUniverse() == null) {
            return "";
        }
        return request.stockUniverse().stream()
                .filter(stock -> resolvedStock.stockCode().equals(stock.stockCode()))
                .map(AlertAnalysisPublishRequest.StockCandidateRequest::stockNameEn)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse("");
    }

    private List<String> displayTranslationQualityFlags(
            List<String> qualityFlags,
            List<AlertGlossaryTerm> displayGlossaryTerms,
            ArticleTranslationResult... translationResults) {
        boolean hasDisplayGlossary = displayGlossaryTerms != null && !displayGlossaryTerms.isEmpty();
        LinkedHashSet<String> flags = new LinkedHashSet<>();
        for (ArticleTranslationResult result : translationResults) {
            if (result != null && result.qualityFlags() != null) {
                flags.addAll(result.qualityFlags());
            }
        }
        if (qualityFlags != null && qualityFlags.contains("FINANCIAL_GLOSSARY_APPLIED")) {
            flags.add("FINANCIAL_GLOSSARY_APPLIED");
        }
        return flags.stream()
                .filter(flag -> hasDisplayGlossary || !"FINANCIAL_GLOSSARY_APPLIED".equals(flag))
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

    private Optional<ArticleTranslationResult> translatedContentFromAnalysis(
            HannahAiAnalysisResponse analysis,
            String originalContent) {
        if (analysis == null || !StringUtils.hasText(originalContent)) {
            return Optional.empty();
        }
        if (!ArticleTranslationResult.STATUS_TRANSLATED.equals(analysis.translationStatus())) {
            return Optional.empty();
        }
        String englishText = EnglishNewsQualityGate.englishTextOrEmpty(analysis.translatedContent());
        if (!StringUtils.hasText(englishText)) {
            return Optional.empty();
        }
        if (isLikelyIncompleteTranslation(originalContent, englishText)) {
            return Optional.empty();
        }
        if (EnglishNewsQualityGate.looksLikeStructuredSummaryContent(englishText)) {
            return Optional.empty();
        }
        return Optional.of(new ArticleTranslationResult(
                englishText,
                firstText(analysis.translationProvider(), "hannah-ai-analysis"),
                firstText(analysis.translationModelVersion(), analysis.modelVersion()),
                ArticleTranslationResult.STATUS_TRANSLATED,
                analysis.translationQualityFlags()));
    }

    private Optional<ArticleTranslationResult> translatedTitleFromAnalysis(HannahAiAnalysisResponse analysis) {
        if (analysis == null || !ArticleTranslationResult.STATUS_TRANSLATED.equals(analysis.translationStatus())) {
            return Optional.empty();
        }
        String englishTitle = EnglishNewsQualityGate.englishTextOrEmpty(analysis.translatedTitle());
        if (!EnglishNewsQualityGate.hasUsableEnglishHeadlineText(englishTitle)) {
            return Optional.empty();
        }
        return Optional.of(analysisTranslationResult(englishTitle, analysis));
    }

    private Optional<ArticleTranslationResult> translatedSummaryFromAnalysis(HannahAiAnalysisResponse analysis) {
        if (analysis == null || !ArticleTranslationResult.STATUS_TRANSLATED.equals(analysis.translationStatus())) {
            return Optional.empty();
        }
        String englishSummary = EnglishNewsQualityGate.englishTextOrEmpty(analysis.translatedSummary());
        if (!StringUtils.hasText(englishSummary)) {
            return Optional.empty();
        }
        return Optional.of(analysisTranslationResult(englishSummary, analysis));
    }

    private ArticleTranslationResult analysisTranslationResult(
            String translatedText,
            HannahAiAnalysisResponse analysis) {
        return new ArticleTranslationResult(
                translatedText,
                firstText(analysis.translationProvider(), "hannah-ai-analysis"),
                firstText(analysis.translationModelVersion(), analysis.modelVersion()),
                ArticleTranslationResult.STATUS_TRANSLATED,
                analysis.translationQualityFlags());
    }

    private String textOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private String requireEnglishText(ArticleTranslationResult result, String context) {
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

    private String translationFailureDetails(ArticleTranslationResult result) {
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
            ArticleTranslationResult result,
            String originalContent,
            String context) {
        if (!StringUtils.hasText(originalContent)) {
            return "";
        }
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
        if (!ArticleTranslationResult.STATUS_TRANSLATED.equals(result.status())) {
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

    private boolean hasCompleteEnglishTranslationStatus(ArticleTranslationResult result) {
        return result != null
                && ArticleTranslationResult.STATUS_TRANSLATED.equals(result.status())
                && EnglishNewsQualityGate.hasUsableEnglishText(result.translatedText())
                && !hasFatalTranslationQualityFlag(result);
    }

    private boolean hasFatalTranslationQualityFlag(ArticleTranslationResult result) {
        if (result == null || result.qualityFlags() == null || result.qualityFlags().isEmpty()) {
            return false;
        }
        return hasFatalTranslationQualityFlag(result.qualityFlags());
    }

    private boolean hasFatalTranslationQualityFlag(List<String> qualityFlags) {
        if (qualityFlags == null || qualityFlags.isEmpty()) {
            return false;
        }
        return qualityFlags.stream()
                .anyMatch(flag -> StringUtils.hasText(flag)
                        && !"FINANCIAL_GLOSSARY_APPLIED".equals(flag));
    }

    private ArticleTranslationResult translatedSummaryResult(
            String translatedSummaryText,
            ArticleTranslationResult... references) {
        return new ArticleTranslationResult(
                translatedSummaryText,
                translationProvider(references),
                translationModelVersion(references),
                ArticleTranslationResult.STATUS_TRANSLATED);
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

    private String originalContent(HannahAiAnalysisResponse analysis, AlertAnalysisPublishRequest request) {
        if (StringUtils.hasText(request.content())) {
            return request.content();
        }
        return analysis.originalContent() == null ? "" : analysis.originalContent();
    }

    private List<String> imageUrls(HannahAiAnalysisResponse analysis, AlertAnalysisPublishRequest request) {
        if (analysis.imageUrls() != null && !analysis.imageUrls().isEmpty()) {
            return analysis.imageUrls();
        }
        return request.imageUrls() == null ? List.of() : request.imageUrls();
    }

    private String contentAvailability(
            HannahAiAnalysisResponse analysis,
            AlertAnalysisPublishRequest request,
            String translatedContent) {
        String content = StringUtils.hasText(analysis.originalContent())
                && !StringUtils.hasText(request.content())
                ? analysis.originalContent()
                : request.content();
        if (StringUtils.hasText(content)) {
            if (!StringUtils.hasText(translatedContent)) {
                return hasTruncationMarker(content) ? "SUMMARY_ONLY" : "ORIGINAL_TEXT_ONLY";
            }
            return hasTruncationMarker(content) ? "SUMMARY_ONLY" : "FULL_TEXT";
        }
        if (StringUtils.hasText(analysis.contentAvailability())) {
            return analysis.contentAvailability();
        }
        return "SUMMARY_ONLY";
    }

    private String translationProvider(ArticleTranslationResult... results) {
        for (ArticleTranslationResult result : results) {
            if (result != null
                    && StringUtils.hasText(result.provider())
                    && !ArticleTranslationResult.PROVIDER_ALREADY_ENGLISH.equals(result.provider())
                    && !"source-language-fallback".equals(result.provider())) {
                return result.provider();
            }
        }
        for (ArticleTranslationResult result : results) {
            if (result != null && StringUtils.hasText(result.provider())) {
                return result.provider();
            }
        }
        return "source-language-fallback";
    }

    private String translationModelVersion(ArticleTranslationResult... results) {
        for (ArticleTranslationResult result : results) {
            if (result != null
                    && StringUtils.hasText(result.modelVersion())
                    && !ArticleTranslationResult.MODEL_TRANSLATION_UNAVAILABLE.equals(
                            result.modelVersion())) {
                return result.modelVersion();
            }
        }
        for (ArticleTranslationResult result : results) {
            if (result != null && StringUtils.hasText(result.modelVersion())) {
                return result.modelVersion();
            }
        }
        return "";
    }

    private String translationStatus(ArticleTranslationResult... results) {
        boolean translated = false;
        boolean fallback = false;
        for (ArticleTranslationResult result : results) {
            if (!StringUtils.hasText(result.status())) {
                continue;
            }
            translated = translated
                    || ArticleTranslationResult.STATUS_TRANSLATED.equals(result.status())
                    || ArticleTranslationResult.STATUS_PARTIAL_SOURCE_LANGUAGE_FALLBACK.equals(result.status());
            fallback = fallback
                    || ArticleTranslationResult.STATUS_SOURCE_LANGUAGE_FALLBACK.equals(result.status())
                    || ArticleTranslationResult.STATUS_PARTIAL_SOURCE_LANGUAGE_FALLBACK.equals(result.status());
        }
        if (translated && fallback) {
            return ArticleTranslationResult.STATUS_PARTIAL_SOURCE_LANGUAGE_FALLBACK;
        }
        return translated
                ? ArticleTranslationResult.STATUS_TRANSLATED
                : ArticleTranslationResult.STATUS_SOURCE_LANGUAGE_FALLBACK;
    }

    private String firstText(String preferred, String fallback) {
        return StringUtils.hasText(preferred) ? preferred : fallback;
    }

    private record ResolvedStock(
            String stockCode,
            String stockName,
            List<String> relatedStocks,
            double stockMatchConfidence) {
    }
}

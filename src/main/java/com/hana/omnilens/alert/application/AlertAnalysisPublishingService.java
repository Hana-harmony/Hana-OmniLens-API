package com.hana.omnilens.alert.application;

import java.time.Instant;
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

import com.hana.omnilens.alert.api.AlertAnalysisPublishRequest;
import com.hana.omnilens.alert.api.AlertPublishRequest;
import com.hana.omnilens.alert.application.AlertTitleTranslationService.TranslationResult;
import com.hana.omnilens.alert.domain.AlertGlossaryTerm;
import com.hana.omnilens.alert.domain.AlertEvent;
import com.hana.omnilens.alert.domain.AlertSummaryLines;
import com.hana.omnilens.provider.ai.HannahAiAnalysisClient;
import com.hana.omnilens.provider.ai.HannahAiAnalysisRequest;
import com.hana.omnilens.provider.ai.HannahAiAnalysisResponse;
import com.hana.omnilens.provider.ai.HannahAiGlossaryTerm;
import com.hana.omnilens.provider.ai.HannahAiStockCandidate;

@Service
public class AlertAnalysisPublishingService {

    private static final Logger log = LoggerFactory.getLogger(AlertAnalysisPublishingService.class);

    private final HannahAiAnalysisClient hannahAiAnalysisClient;
    private final AlertStreamingService alertStreamingService;
    private final AlertTitleTranslationService alertTitleTranslationService;
    private final AlertEventRepository alertEventRepository;
    private final KoreanMarketGlossaryTermExtractor glossaryTermExtractor = new KoreanMarketGlossaryTermExtractor();

    public AlertAnalysisPublishingService(
            HannahAiAnalysisClient hannahAiAnalysisClient,
            AlertStreamingService alertStreamingService,
            AlertTitleTranslationService alertTitleTranslationService,
            AlertEventRepository alertEventRepository) {
        this.hannahAiAnalysisClient = hannahAiAnalysisClient;
        this.alertStreamingService = alertStreamingService;
        this.alertTitleTranslationService = alertTitleTranslationService;
        this.alertEventRepository = alertEventRepository;
    }

    public AlertEvent analyzeAndPublish(AlertAnalysisPublishRequest request) {
        return publishAnalyzed(analyze(request));
    }

    public AlertPublishRequest analyze(AlertAnalysisPublishRequest request) {
        return analyze(request, false);
    }

    private AlertPublishRequest analyze(AlertAnalysisPublishRequest request, boolean allowSingleStockFallback) {
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
                toStockUniverse(request.stockUniverse())));

        ResolvedStock resolvedStock = resolveStock(analysis, request, allowSingleStockFallback);
        if (!StringUtils.hasText(resolvedStock.stockCode()) || !StringUtils.hasText(resolvedStock.stockName())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "AI analysis did not match a stock");
        }
        List<AlertGlossaryTerm> glossaryTerms = withResolvedStockGlossary(
                toAlertGlossaryTerms(analysis.glossaryTerms()),
                resolvedStock,
                request);
        String originalContent = originalContent(analysis, request);
        TranslationResult translatedTitle = alertTitleTranslationService.translateTitleWithResult(
                analysis.originalTitle(),
                glossaryTerms);
        String translatedTitleText = requireEnglishText(translatedTitle, "alert title");
        AlertSummaryLines sourceSummaryLines = sourceSummaryLines(
                analysis.summary(),
                analysis.summaryLines(),
                analysis.originalTitle());
        String sourceSummary = joinSummaryLines(sourceSummaryLines);
        AlertSummaryLines alreadyEnglishSummaryLines = EnglishNewsQualityGate.englishSummaryLinesOrEmpty(
                sourceSummaryLines);
        boolean alreadyEnglishSummary = EnglishNewsQualityGate.hasUsableEnglishSummaryLines(
                alreadyEnglishSummaryLines);
        TranslationResult translatedSummary = alreadyEnglishSummary
                ? alreadyEnglishSummaryResult(alreadyEnglishSummaryLines, translatedTitle)
                : alertTitleTranslationService.translateTextWithResult(sourceSummary, glossaryTerms);
        TranslationResult translatedContent = translateContent(originalContent, glossaryTerms);
        AlertSummaryLines translatedSummaryLines = alreadyEnglishSummary
                ? alreadyEnglishSummaryLines
                : requireEnglishSummaryLines(
                        translateSummaryLines(
                                sourceSummaryLines,
                                glossaryTerms),
                        translatedSummary,
                        "alert summary");
        String translatedSummaryText = joinSummaryLines(translatedSummaryLines);
        String translatedContentText = requireOptionalEnglishContent(
                translatedContent,
                originalContent,
                "alert content");
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

        return new AlertPublishRequest(
                request.partnerId(),
                resolvedStock.stockCode(),
                resolvedStock.stockName(),
                analysis.sourceType(),
                analysis.originalTitle(),
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
                resolvedStock.relatedStocks(),
                analysis.holderTarget(),
                analysis.watchlistTarget(),
                displayGlossaryTerms,
                displayTranslationQualityFlags(analysis.translationQualityFlags(), displayGlossaryTerms),
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

    public Optional<AlertEvent> repairQualityIssueIfPossible(AlertEvent event) {
        try {
            return Optional.of(reprocess(event));
        } catch (RuntimeException reprocessException) {
            log.warn(
                    "Falling back to alert summary quality repair after reprocess failed: alertId={}, stockCode={}",
                    event.alertId(),
                    event.stockCode(),
                    reprocessException);
        }
        try {
            return Optional.of(repairQualityIssue(event));
        } catch (RuntimeException exception) {
            log.warn(
                    "Skipping alert summary quality repair: alertId={}, stockCode={}",
                    event.alertId(),
                    event.stockCode(),
                    exception);
            return Optional.empty();
        }
    }

    public List<AlertEvent> reprocessSummaryQualityIssues(int limit) {
        int effectiveLimit = Math.max(1, Math.min(limit, 100));
        return alertEventRepository.findSummaryQualityIssues(effectiveLimit).stream()
                .map(this::repairQualityIssueIfPossible)
                .flatMap(Optional::stream)
                .toList();
    }

    private AlertEvent repairQualityIssue(AlertEvent event) {
        String translatedTitle = repairedTranslatedTitle(event);
        AlertSummaryLines summaryLines = repairedSummaryLines(event);
        String translatedSummary = joinSummaryLines(summaryLines);
        String translatedContent = repairedTranslatedContent(event);
        String contentAvailability = repairedContentAvailability(event, translatedContent);
        boolean repaired = !safeEquals(translatedTitle, event.translatedTitle())
                || !safeEquals(translatedSummary, event.translatedSummary())
                || !safeEquals(translatedContent, event.translatedContent())
                || !safeEquals(contentAvailability, event.contentAvailability())
                || !safeEquals(joinSummaryLines(summaryLines), joinSummaryLines(event.summaryLines()));
        AlertEvent repairedEvent = new AlertEvent(
                event.alertId(),
                event.partnerId(),
                event.stockCode(),
                event.stockName(),
                event.sourceType(),
                event.originalTitle(),
                translatedTitle,
                event.summary(),
                summaryLines,
                translatedSummary,
                event.originalContent(),
                translatedContent,
                event.imageUrls(),
                contentAvailability,
                event.originalUrl(),
                event.publishedAt(),
                event.eventTags(),
                event.sentiment(),
                event.importance(),
                event.relatedStocks(),
                event.holderTarget(),
                event.watchlistTarget(),
                event.glossaryTerms(),
                event.translationQualityFlags(),
                repaired ? AlertTitleTranslationService.PROVIDER_LOCAL_OPEN_SOURCE_QWEN : event.translationProvider(),
                event.translationModelVersion(),
                repaired ? AlertTitleTranslationService.STATUS_TRANSLATED : event.translationStatus(),
                event.duplicateKey(),
                event.clusterKey(),
                event.modelVersion(),
                event.eventConfidence(),
                event.sentimentConfidence(),
                event.importanceConfidence(),
                event.stockMatchConfidence(),
                event.createdAt());
        return alertEventRepository.save(repairedEvent);
    }

    private String repairedTranslatedTitle(AlertEvent event) {
        String currentTitle = EnglishNewsQualityGate.englishTextOrEmpty(event.translatedTitle());
        if (StringUtils.hasText(currentTitle)) {
            return currentTitle;
        }
        return requireEnglishText(
                alertTitleTranslationService.translateTitleWithResult(event.originalTitle(), event.glossaryTerms()),
                "alert title repair");
    }

    private AlertSummaryLines repairedSummaryLines(AlertEvent event) {
        AlertSummaryLines currentLines = EnglishNewsQualityGate.englishSummaryLinesOrEmpty(event.summaryLines());
        if (EnglishNewsQualityGate.hasUsableEnglishSummaryLines(currentLines)) {
            return currentLines;
        }
        AlertSummaryLines sourceSummaryLines = sourceSummaryLines(event.summary(), null, event.originalTitle());
        TranslationResult translatedSummary = alertTitleTranslationService.translateTextWithResult(
                joinSummaryLines(sourceSummaryLines),
                event.glossaryTerms());
        return requireEnglishSummaryLines(
                translateSummaryLines(sourceSummaryLines, event.glossaryTerms()),
                translatedSummary,
                "alert summary repair");
    }

    private String repairedTranslatedContent(AlertEvent event) {
        if (EnglishNewsQualityGate.hasUsableEnglishText(event.translatedContent())
                && !EnglishNewsQualityGate.looksLikeSummaryOnlyContent(
                        event.translatedContent(),
                        event.summaryLines(),
                        event.translatedSummary(),
                        event.originalContent())) {
            return EnglishNewsQualityGate.englishTextOrEmpty(event.translatedContent());
        }
        if (!StringUtils.hasText(event.originalContent())) {
            return "";
        }
        try {
            TranslationResult translatedOriginalContent = translateContent(event.originalContent(), event.glossaryTerms());
            if (translatedOriginalContent == null) {
                throw new IllegalStateException("English translation failed: alert content repair");
            }
            return requireOptionalEnglishContent(
                    translatedOriginalContent,
                    event.originalContent(),
                    "alert content repair");
        } catch (RuntimeException exception) {
            log.warn("Failed to retranslate alert content during quality repair: alertId={}", event.alertId(), exception);
            throw exception;
        }
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
            List<AlertGlossaryTerm> displayGlossaryTerms) {
        if (qualityFlags == null || qualityFlags.isEmpty()) {
            return List.of();
        }
        boolean hasDisplayGlossary = displayGlossaryTerms != null && !displayGlossaryTerms.isEmpty();
        return qualityFlags.stream()
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

    private TranslationResult translateContent(String originalContent, List<AlertGlossaryTerm> glossaryTerms) {
        if (!StringUtils.hasText(originalContent)) {
            return new TranslationResult("", "", "", "");
        }
        return alertTitleTranslationService.translateTextWithResult(originalContent, glossaryTerms);
    }

    private String textOrEmpty(String value) {
        return value == null ? "" : value;
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
        return "status=%s, provider=%s, model=%s, length=%d, hasHangul=%s, lowQuality=%s, genericFallback=%s"
                .formatted(
                        result.status(),
                        result.provider(),
                        result.modelVersion(),
                        translatedText.length(),
                        EnglishNewsQualityGate.containsHangul(translatedText),
                        EnglishNewsQualityGate.containsLowQualityTranslation(translatedText),
                        EnglishNewsQualityGate.containsGenericFallback(translatedText));
    }

    private String requireOptionalEnglishContent(
            TranslationResult result,
            String originalContent,
            String context) {
        if (!StringUtils.hasText(originalContent)) {
            return "";
        }
        if (!requiresCompleteContentTranslation(originalContent)) {
            if (result == null || !hasEnglishTranslationStatus(result)) {
                return "";
            }
            return EnglishNewsQualityGate.englishTextOrEmpty(result.translatedText());
        }
        if (result == null) {
            throw new IllegalStateException("English translation failed: "
                    + context + " (" + translationFailureDetails(result) + ")");
        }
        if (!AlertTitleTranslationService.STATUS_TRANSLATED.equals(result.status())
                && EnglishNewsQualityGate.containsHangul(originalContent)) {
            return "";
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
            return EnglishNewsQualityGate.containsHangul(originalContent)
                    ? ""
                    : englishText;
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
        return normalized.length() >= 160
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
        List<String> sentences = new java.util.ArrayList<>();
        while (matcher.find()) {
            String sentence = EnglishNewsQualityGate.englishSummaryLineOrEmpty(matcher.group());
            if (StringUtils.hasText(sentence)) {
                sentences.add(sentence);
            }
        }
        return sentences;
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
        TranslationResult result = alertTitleTranslationService.translateTextWithResult(value, glossaryTerms);
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
                && (AlertTitleTranslationService.STATUS_TRANSLATED.equals(result.status())
                || (AlertTitleTranslationService.STATUS_SOURCE_LANGUAGE_FALLBACK.equals(result.status())
                && EnglishNewsQualityGate.hasUsableEnglishText(result.translatedText())));
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

    private boolean safeEquals(String left, String right) {
        return (left == null ? "" : left).equals(right == null ? "" : right);
    }

    private String originalContent(HannahAiAnalysisResponse analysis, AlertAnalysisPublishRequest request) {
        if (StringUtils.hasText(analysis.originalContent())) {
            return analysis.originalContent();
        }
        return request.content() == null ? "" : request.content();
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

    private String repairedContentAvailability(AlertEvent event, String translatedContent) {
        if (!StringUtils.hasText(event.originalContent())) {
            return StringUtils.hasText(event.contentAvailability())
                    ? event.contentAvailability()
                    : "DISCOVERY_ONLY";
        }
        if (!StringUtils.hasText(translatedContent)) {
            return hasTruncationMarker(event.originalContent()) ? "SUMMARY_ONLY" : "ORIGINAL_TEXT_ONLY";
        }
        return hasTruncationMarker(event.originalContent()) ? "SUMMARY_ONLY" : "FULL_TEXT";
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

    private record ResolvedStock(
            String stockCode,
            String stockName,
            List<String> relatedStocks,
            double stockMatchConfidence) {
    }
}

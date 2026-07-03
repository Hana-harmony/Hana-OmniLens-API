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
                request.canonicalUrl(),
                request.contentHash(),
                request.sourceLicensePolicy(),
                request.originalUrl(),
                toStockUniverse(request.stockUniverse())));

        ResolvedStock resolvedStock = resolveStock(analysis, request, allowSingleStockFallback);
        if (!StringUtils.hasText(resolvedStock.stockCode()) || !StringUtils.hasText(resolvedStock.stockName())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "AI analysis did not match a stock");
        }
        List<AlertGlossaryTerm> glossaryTerms = toAlertGlossaryTerms(analysis.glossaryTerms());
        String originalContent = originalContent(analysis, request);
        TranslationResult translatedTitle = alertTitleTranslationService.translateTitleWithResult(
                analysis.originalTitle(),
                glossaryTerms);
        String sourceSummary = summaryText(analysis.summary(), analysis.summaryLines(), analysis.originalTitle());
        TranslationResult translatedSummary = alertTitleTranslationService.translateTextWithResult(
                sourceSummary,
                glossaryTerms);
        TranslationResult translatedContent = translateContent(originalContent, glossaryTerms);
	        AlertSummaryLines translatedSummaryLines = translateSummaryLines(
	                analysis.summaryLines(),
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

        return new AlertPublishRequest(
                request.partnerId(),
                resolvedStock.stockCode(),
                resolvedStock.stockName(),
                analysis.sourceType(),
                analysis.originalTitle(),
                translatedTitle.translatedText(),
                sourceSummary,
                translatedSummaryLines,
                translatedSummary.translatedText(),
                originalContent,
                translatedContent.translatedText(),
                imageUrls(analysis, request),
                contentAvailability(analysis, request),
                request.originalUrl(),
                request.publishedAt(),
                analysis.eventTags(),
                analysis.sentiment(),
                analysis.importance(),
                resolvedStock.relatedStocks(),
                analysis.holderTarget(),
                analysis.watchlistTarget(),
                displayGlossaryTerms,
                analysis.translationQualityFlags() == null ? List.of() : analysis.translationQualityFlags(),
                translationProvider(translatedTitle, translatedSummary, translatedContent),
                translationModelVersion(translatedTitle, translatedSummary, translatedContent),
                translationStatus(translatedTitle, translatedSummary, translatedContent),
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

	    private TranslationResult translateContent(String originalContent, List<AlertGlossaryTerm> glossaryTerms) {
	        if (!StringUtils.hasText(originalContent)) {
	            return new TranslationResult("", "", "", "");
	        }
	        return alertTitleTranslationService.translateTextWithResult(originalContent, glossaryTerms);
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
	        return sanitizeSummary(alertTitleTranslationService.translateTextWithResult(value, glossaryTerms).translatedText());
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

    private String contentAvailability(HannahAiAnalysisResponse analysis, AlertAnalysisPublishRequest request) {
        if (StringUtils.hasText(analysis.originalContent()) || StringUtils.hasText(request.content())) {
            return "FULL_TEXT";
        }
        if (StringUtils.hasText(analysis.contentAvailability())) {
            return analysis.contentAvailability();
        }
        return "SUMMARY_ONLY";
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

    private record ResolvedStock(
            String stockCode,
            String stockName,
            List<String> relatedStocks,
            double stockMatchConfidence) {
    }
}

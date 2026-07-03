package com.hana.omnilens.alert.application;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

        if (!StringUtils.hasText(analysis.stockCode()) || !StringUtils.hasText(analysis.stockName())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "AI analysis did not match a stock");
        }
        List<AlertGlossaryTerm> glossaryTerms = toAlertGlossaryTerms(analysis.glossaryTerms());
        String originalContent = originalContent(analysis, request);
        TranslationResult translatedTitle = alertTitleTranslationService.translateTitleWithResult(
                analysis.originalTitle(),
                glossaryTerms);
        TranslationResult translatedSummary = alertTitleTranslationService.translateTextWithResult(
                analysis.summary(),
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
                analysis.stockCode(),
                analysis.stockName(),
                analysis.sourceType(),
	                analysis.originalTitle(),
	                translatedTitle.translatedText(),
	                analysis.summary(),
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
                analysis.relatedStocks(),
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
                analysis.stockMatchConfidence());
    }

    public AlertEvent publishAnalyzed(AlertPublishRequest request) {
        return alertStreamingService.publish(request);
    }

    public AlertEvent reprocess(AlertEvent event) {
        AlertPublishRequest analyzed = analyze(new AlertAnalysisPublishRequest(
                event.partnerId(),
                event.sourceType(),
                event.originalTitle(),
                event.summary(),
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
                        List.of(event.stockName())))));
        AlertEvent updated = toExistingEvent(event.alertId(), event.createdAt(), analyzed);
        return alertEventRepository.save(updated);
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
	        return alertTitleTranslationService.translateTextWithResult(value, glossaryTerms).translatedText();
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
}

package com.hana.omnilens.alert.application;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.hana.omnilens.alert.api.AlertAnalysisPublishRequest;
import com.hana.omnilens.alert.api.AlertPublishRequest;
import com.hana.omnilens.alert.domain.AlertGlossaryTerm;
import com.hana.omnilens.alert.domain.AlertEvent;
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

    public AlertAnalysisPublishingService(
            HannahAiAnalysisClient hannahAiAnalysisClient,
            AlertStreamingService alertStreamingService,
            AlertTitleTranslationService alertTitleTranslationService) {
        this.hannahAiAnalysisClient = hannahAiAnalysisClient;
        this.alertStreamingService = alertStreamingService;
        this.alertTitleTranslationService = alertTitleTranslationService;
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

        return new AlertPublishRequest(
                request.partnerId(),
                analysis.stockCode(),
                analysis.stockName(),
                analysis.sourceType(),
                analysis.originalTitle(),
                alertTitleTranslationService.translateTitle(analysis.originalTitle()),
                analysis.summary(),
                analysis.summaryLines(),
                alertTitleTranslationService.translateText(analysis.summary()),
                originalContent(analysis, request),
                translateContent(originalContent(analysis, request)),
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
                toAlertGlossaryTerms(analysis.glossaryTerms()),
                analysis.translationQualityFlags() == null ? List.of() : analysis.translationQualityFlags(),
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

    private String translateContent(String originalContent) {
        if (!StringUtils.hasText(originalContent)) {
            return "";
        }
        return alertTitleTranslationService.translateText(originalContent);
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
}

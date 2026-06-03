package com.hana.omnilens.alert.application;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.hana.omnilens.alert.api.AlertAnalysisPublishRequest;
import com.hana.omnilens.alert.api.AlertPublishRequest;
import com.hana.omnilens.alert.domain.AlertEvent;
import com.hana.omnilens.provider.ai.HannahAiAnalysisClient;
import com.hana.omnilens.provider.ai.HannahAiAnalysisRequest;
import com.hana.omnilens.provider.ai.HannahAiAnalysisResponse;
import com.hana.omnilens.provider.ai.HannahAiStockCandidate;

@Service
public class AlertAnalysisPublishingService {

    private final HannahAiAnalysisClient hannahAiAnalysisClient;
    private final AlertStreamingService alertStreamingService;

    public AlertAnalysisPublishingService(
            HannahAiAnalysisClient hannahAiAnalysisClient,
            AlertStreamingService alertStreamingService) {
        this.hannahAiAnalysisClient = hannahAiAnalysisClient;
        this.alertStreamingService = alertStreamingService;
    }

    public AlertEvent analyzeAndPublish(AlertAnalysisPublishRequest request) {
        HannahAiAnalysisResponse analysis = hannahAiAnalysisClient.analyze(new HannahAiAnalysisRequest(
                request.sourceType(),
                request.title(),
                request.snippet() == null ? "" : request.snippet(),
                request.originalUrl(),
                toStockUniverse(request.stockUniverse())));

        if (!StringUtils.hasText(analysis.stockCode()) || !StringUtils.hasText(analysis.stockName())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "AI analysis did not match a stock");
        }

        return alertStreamingService.publish(new AlertPublishRequest(
                request.partnerId(),
                analysis.stockCode(),
                analysis.stockName(),
                analysis.sourceType(),
                analysis.originalTitle(),
                analysis.originalTitle(),
                analysis.summary(),
                request.originalUrl(),
                request.publishedAt(),
                analysis.eventTags(),
                analysis.sentiment(),
                analysis.importance(),
                analysis.relatedStocks(),
                analysis.holderTarget(),
                analysis.watchlistTarget(),
                analysis.duplicateKey(),
                analysis.modelVersion()));
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
}

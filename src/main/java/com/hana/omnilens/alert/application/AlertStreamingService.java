package com.hana.omnilens.alert.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import com.hana.omnilens.alert.api.AlertPublishRequest;
import com.hana.omnilens.alert.domain.AlertEvent;
import com.hana.omnilens.alert.domain.AlertGlossaryTerm;
import com.hana.omnilens.alert.domain.AlertSummaryLines;
import com.hana.omnilens.alert.stream.AlertEventRawStreamingService;

@Service
public class AlertStreamingService {

    private final SimpMessagingTemplate messagingTemplate;
    private final AlertEventRepository alertEventRepository;
    private final AlertEventRawStreamingService rawStreamingService;
    private final KoreanMarketGlossaryTermExtractor glossaryTermExtractor = new KoreanMarketGlossaryTermExtractor();

    public AlertStreamingService(
            SimpMessagingTemplate messagingTemplate,
            AlertEventRepository alertEventRepository,
            AlertEventRawStreamingService rawStreamingService) {
        this.messagingTemplate = messagingTemplate;
        this.alertEventRepository = alertEventRepository;
        this.rawStreamingService = rawStreamingService;
    }

    public AlertEvent publish(AlertPublishRequest request) {
        List<AlertGlossaryTerm> glossaryTerms = glossaryTermExtractor.filterDisplayableTerms(
                request.glossaryTerms() == null ? List.of() : request.glossaryTerms());
        AlertEvent event = new AlertEvent(
                UUID.randomUUID().toString(),
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
                glossaryTerms,
                displayTranslationQualityFlags(request.translationQualityFlags(), glossaryTerms),
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
                Instant.now());

        AlertEvent storedEvent = alertEventRepository.save(event);
        if (!storedEvent.alertId().equals(event.alertId())) {
            return storedEvent;
        }
        messagingTemplate.convertAndSend("/topic/partners/" + request.partnerId() + "/alerts", storedEvent);
        messagingTemplate.convertAndSend(
                "/topic/partners/" + request.partnerId() + "/stocks/" + request.stockCode() + "/alerts",
                storedEvent);
        rawStreamingService.publish(storedEvent);
        return storedEvent;
    }

    private List<String> displayTranslationQualityFlags(
            List<String> qualityFlags,
            List<AlertGlossaryTerm> glossaryTerms) {
        if (qualityFlags == null || qualityFlags.isEmpty()) {
            return List.of();
        }
        if (glossaryTerms != null && !glossaryTerms.isEmpty()) {
            return qualityFlags;
        }
        return qualityFlags.stream()
                .filter(flag -> !"FINANCIAL_GLOSSARY_APPLIED".equals(flag))
                .toList();
    }
}

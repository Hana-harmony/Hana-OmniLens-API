package com.hana.omnilens.alert.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import com.hana.omnilens.alert.api.AlertPublishRequest;
import com.hana.omnilens.alert.domain.AlertEvent;
import com.hana.omnilens.alert.domain.AlertSummaryLines;

@Service
public class AlertStreamingService {

    private final SimpMessagingTemplate messagingTemplate;
    private final AlertEventRepository alertEventRepository;

    public AlertStreamingService(
            SimpMessagingTemplate messagingTemplate,
            AlertEventRepository alertEventRepository) {
        this.messagingTemplate = messagingTemplate;
        this.alertEventRepository = alertEventRepository;
    }

    public AlertEvent publish(AlertPublishRequest request) {
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
                request.relatedStocks(),
                request.holderTarget(),
                request.watchlistTarget(),
                request.glossaryTerms() == null ? List.of() : request.glossaryTerms(),
                request.translationQualityFlags() == null ? List.of() : request.translationQualityFlags(),
                request.duplicateKey(),
                request.clusterKey(),
                request.modelVersion(),
                request.eventConfidence(),
                request.sentimentConfidence(),
                request.importanceConfidence(),
                request.stockMatchConfidence(),
                Instant.now());

        alertEventRepository.save(event);
        messagingTemplate.convertAndSend("/topic/partners/" + request.partnerId() + "/alerts", event);
        messagingTemplate.convertAndSend(
                "/topic/partners/" + request.partnerId() + "/stocks/" + request.stockCode() + "/alerts",
                event);
        messagingTemplate.convertAndSend("/topic/stocks/" + request.stockCode() + "/alerts", event);
        return event;
    }
}

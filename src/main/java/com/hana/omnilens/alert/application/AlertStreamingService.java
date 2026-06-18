package com.hana.omnilens.alert.application;

import java.time.Instant;
import java.util.UUID;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import com.hana.omnilens.alert.api.AlertPublishRequest;
import com.hana.omnilens.alert.domain.AlertEvent;

@Service
public class AlertStreamingService {

    private final SimpMessagingTemplate messagingTemplate;

    public AlertStreamingService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
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
                request.originalUrl(),
                request.publishedAt(),
                request.eventTags(),
                request.sentiment(),
                request.importance(),
                request.relatedStocks(),
                request.holderTarget(),
                request.watchlistTarget(),
                request.duplicateKey(),
                request.modelVersion(),
                Instant.now());

        messagingTemplate.convertAndSend("/topic/partners/" + request.partnerId() + "/alerts", event);
        messagingTemplate.convertAndSend(
                "/topic/partners/" + request.partnerId() + "/stocks/" + request.stockCode() + "/alerts",
                event);
        messagingTemplate.convertAndSend("/topic/stocks/" + request.stockCode() + "/alerts", event);
        return event;
    }
}

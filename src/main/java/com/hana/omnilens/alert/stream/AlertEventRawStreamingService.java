package com.hana.omnilens.alert.stream;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hana.omnilens.alert.domain.AlertEvent;
import com.hana.omnilens.alert.domain.AlertSummaryLines;

@Service
public class AlertEventRawStreamingService {

    private final Set<WebSocketSession> sessions = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final ObjectMapper objectMapper;

    public AlertEventRawStreamingService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void register(WebSocketSession session) {
        sessions.add(session);
    }

    public void unregister(WebSocketSession session) {
        sessions.remove(session);
    }

    public void publish(AlertEvent event) {
        if (sessions.isEmpty()) {
            return;
        }
        String payload;
        try {
            payload = objectMapper.writeValueAsString(toPayload(event));
        } catch (IOException exception) {
            throw new IllegalStateException("Alert stream payload serialization failed", exception);
        }
        TextMessage message = new TextMessage(payload);
        for (WebSocketSession session : sessions) {
            send(session, message);
        }
    }

    private void send(WebSocketSession session, TextMessage message) {
        if (!session.isOpen()) {
            sessions.remove(session);
            return;
        }
        try {
            session.sendMessage(message);
        } catch (IOException exception) {
            sessions.remove(session);
        }
    }

    private AlertEventStreamPayload toPayload(AlertEvent event) {
        String idempotencyKey = firstText(event.duplicateKey(), event.clusterKey(), event.alertId());
        return new AlertEventStreamPayload(
                event.alertId(),
                idempotencyKey,
                event.sourceType(),
                firstText(event.translatedTitle(), event.originalTitle(), event.summary()),
                event.summary(),
                event.summaryLines() == null ? AlertSummaryLines.fromSummary(event.summary()) : event.summaryLines(),
                blankToEmpty(event.translatedSummary()),
                blankToEmpty(event.originalContent()),
                blankToEmpty(event.translatedContent()),
                event.imageUrls() == null ? List.of() : event.imageUrls(),
                firstText(event.contentAvailability(), "SUMMARY_ONLY"),
                event.originalUrl(),
                event.stockCode(),
                relatedStocks(event),
                event.glossaryTerms() == null ? List.of() : event.glossaryTerms(),
                event.translationQualityFlags() == null ? List.of() : event.translationQualityFlags(),
                firstText(event.translationProvider(), "source-language-fallback"),
                blankToEmpty(event.translationModelVersion()),
                firstText(event.translationStatus(), "SOURCE_LANGUAGE_FALLBACK"),
                firstText(event.clusterKey(), idempotencyKey),
                firstText(event.sentiment(), "NEUTRAL"),
                firstText(event.importance(), "MEDIUM"),
                riskLevel(event.importance()),
                event.watchlistTarget(),
                event.holderTarget(),
                event.publishedAt());
    }

    private List<String> relatedStocks(AlertEvent event) {
        LinkedHashSet<String> stocks = new LinkedHashSet<>();
        if (event.relatedStocks() != null) {
            stocks.addAll(event.relatedStocks());
        }
        if (StringUtils.hasText(event.stockCode())) {
            stocks.add(event.stockCode());
        }
        return List.copyOf(stocks);
    }

    private String riskLevel(String importance) {
        String value = firstText(importance, "MEDIUM").toUpperCase(java.util.Locale.ROOT);
        if ("CRITICAL".equals(value) || "HIGH".equals(value)) {
            return "HIGH";
        }
        if ("LOW".equals(value)) {
            return "LOW";
        }
        return "MEDIUM";
    }

    private String blankToEmpty(String value) {
        return StringUtils.hasText(value) ? value : "";
    }

    private String firstText(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    private String firstText(String first, String second, String third) {
        if (StringUtils.hasText(first)) {
            return first;
        }
        if (StringUtils.hasText(second)) {
            return second;
        }
        return third;
    }
}

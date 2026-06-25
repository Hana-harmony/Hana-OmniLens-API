package com.hana.omnilens.market.stream;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hana.omnilens.market.application.MarketDataService;
import com.hana.omnilens.market.domain.MarketIndexQuote;

@Service
public class MarketIndexStreamingService {

    private final MarketDataService marketDataService;
    private final ObjectMapper objectMapper;
    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();

    public MarketIndexStreamingService(MarketDataService marketDataService, ObjectMapper objectMapper) {
        this.marketDataService = marketDataService;
        this.objectMapper = objectMapper;
    }

    void register(WebSocketSession session) {
        sessions.add(session);
    }

    void unregister(WebSocketSession session) {
        sessions.remove(session);
    }

    public void publish(MarketIndexQuote indexQuote) {
        String payload = writePayload(indexQuote);
        for (WebSocketSession session : sessions) {
            sendPayload(session, payload);
        }
    }

    void replay(WebSocketSession session) {
        marketDataService.getIndices().forEach(index -> sendPayload(session, writePayload(index)));
    }

    private String writePayload(MarketIndexQuote indexQuote) {
        try {
            return objectMapper.writeValueAsString(indexQuote);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize market index stream payload", exception);
        }
    }

    private void sendPayload(WebSocketSession session, String payload) {
        if (!session.isOpen()) {
            sessions.remove(session);
            return;
        }
        try {
            session.sendMessage(new TextMessage(payload));
        } catch (IOException exception) {
            sessions.remove(session);
        }
    }
}

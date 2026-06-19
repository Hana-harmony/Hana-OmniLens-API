package com.hana.omnilens.market.stream;

import java.io.IOException;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class MarketQuoteWebSocketHandler extends TextWebSocketHandler {

    private final MarketQuoteStreamingService streamingService;
    private final ObjectMapper objectMapper;

    public MarketQuoteWebSocketHandler(MarketQuoteStreamingService streamingService, ObjectMapper objectMapper) {
        this.streamingService = streamingService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        streamingService.register(session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        MarketQuoteReplayRequest request = objectMapper.readValue(message.getPayload(), MarketQuoteReplayRequest.class);
        if (request.isReplayRequest()) {
            streamingService.replay(session, request.replayCurrency());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        streamingService.unregister(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        streamingService.unregister(session);
    }
}

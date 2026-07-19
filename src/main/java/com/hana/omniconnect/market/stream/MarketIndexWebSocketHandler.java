package com.hana.omniconnect.market.stream;

import java.io.IOException;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class MarketIndexWebSocketHandler extends TextWebSocketHandler {

    private final MarketIndexStreamingService streamingService;
    private final ObjectMapper objectMapper;

    public MarketIndexWebSocketHandler(MarketIndexStreamingService streamingService, ObjectMapper objectMapper) {
        this.streamingService = streamingService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        streamingService.register(session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        MarketIndexReplayRequest request = objectMapper.readValue(message.getPayload(), MarketIndexReplayRequest.class);
        if (request.isReplayRequest()) {
            streamingService.replay(session);
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

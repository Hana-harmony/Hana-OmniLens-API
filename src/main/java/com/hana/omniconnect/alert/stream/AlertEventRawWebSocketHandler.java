package com.hana.omniconnect.alert.stream;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class AlertEventRawWebSocketHandler extends TextWebSocketHandler {

    private final AlertEventRawStreamingService streamingService;

    public AlertEventRawWebSocketHandler(AlertEventRawStreamingService streamingService) {
        this.streamingService = streamingService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        streamingService.register(session);
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

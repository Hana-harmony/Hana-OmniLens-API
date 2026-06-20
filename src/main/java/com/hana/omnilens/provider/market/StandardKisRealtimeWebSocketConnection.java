package com.hana.omnilens.provider.market;

import java.net.URI;
import java.util.List;
import java.util.function.Consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class StandardKisRealtimeWebSocketConnection implements KisRealtimeWebSocketConnection {

    private static final Logger log = LoggerFactory.getLogger(StandardKisRealtimeWebSocketConnection.class);

    private final ObjectMapper objectMapper;
    private final StandardWebSocketClient webSocketClient;

    @Autowired
    public StandardKisRealtimeWebSocketConnection(ObjectMapper objectMapper) {
        this(objectMapper, new StandardWebSocketClient());
    }

    StandardKisRealtimeWebSocketConnection(ObjectMapper objectMapper, StandardWebSocketClient webSocketClient) {
        this.objectMapper = objectMapper;
        this.webSocketClient = webSocketClient;
    }

    @Override
    public void connect(
            URI websocketUrl,
            List<KisRealtimeSubscriptionFrame> subscriptionFrames,
            Consumer<String> messageConsumer) {
        log.info(
                "Connecting KIS realtime websocket url={} subscriptionFrameCount={}",
                websocketUrl,
                subscriptionFrames.size());
        webSocketClient.execute(new Handler(subscriptionFrames, messageConsumer), websocketUrl.toString())
                .whenComplete((session, exception) -> {
                    if (exception != null) {
                        log.warn("KIS realtime websocket connection failed: {}", exception.toString());
                    } else {
                        log.info("KIS realtime websocket connected sessionId={}", session.getId());
                    }
                });
    }

    private class Handler extends TextWebSocketHandler {

        private final List<KisRealtimeSubscriptionFrame> subscriptionFrames;
        private final Consumer<String> messageConsumer;

        private Handler(List<KisRealtimeSubscriptionFrame> subscriptionFrames, Consumer<String> messageConsumer) {
            this.subscriptionFrames = subscriptionFrames;
            this.messageConsumer = messageConsumer;
        }

        @Override
        public void afterConnectionEstablished(WebSocketSession session) throws Exception {
            for (KisRealtimeSubscriptionFrame frame : subscriptionFrames) {
                session.sendMessage(new TextMessage(serialize(frame)));
            }
            log.info(
                    "KIS realtime websocket subscription frames sent sessionId={} subscriptionFrameCount={}",
                    session.getId(),
                    subscriptionFrames.size());
        }

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) {
            log.debug("KIS realtime websocket message received sessionId={}", session.getId());
            messageConsumer.accept(message.getPayload());
        }
    }

    private String serialize(KisRealtimeSubscriptionFrame frame) throws JsonProcessingException {
        return objectMapper.writeValueAsString(frame);
    }
}

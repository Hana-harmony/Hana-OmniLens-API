package com.hana.omnilens.provider.market;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PreDestroy;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.WebSocketContainer;

import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PongMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class StandardKisRealtimeWebSocketConnection implements KisRealtimeWebSocketConnection {

    private static final Logger log = LoggerFactory.getLogger(StandardKisRealtimeWebSocketConnection.class);
    private static final int KIS_MESSAGE_BUFFER_SIZE_BYTES = 1024 * 1024;
    private static final int MAX_RECONNECT_DELAY_SECONDS = 30;
    private static final int MAX_FAST_RECONNECT_ATTEMPTS = 6;
    private static final int GATEWAY_COOLDOWN_SECONDS = 15 * 60;
    private static final long SUBSCRIPTION_FRAME_DELAY_MILLIS = 120;

    private final ObjectMapper objectMapper;
    private final StandardWebSocketClient webSocketClient;
    private final ScheduledExecutorService reconnectExecutor;
    private final AtomicReference<WebSocketSession> session = new AtomicReference<>();
    private final Map<String, KisRealtimeSubscriptionFrame> subscriptionFrames = new ConcurrentHashMap<>();

    @Autowired
    public StandardKisRealtimeWebSocketConnection(ObjectMapper objectMapper) {
        this(objectMapper, standardWebSocketClient());
    }

    StandardKisRealtimeWebSocketConnection(ObjectMapper objectMapper, StandardWebSocketClient webSocketClient) {
        this.objectMapper = objectMapper;
        this.webSocketClient = webSocketClient;
        this.reconnectExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "kis-realtime-reconnect");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Override
    public void connect(
            URI websocketUrl,
            List<KisRealtimeSubscriptionFrame> subscriptionFrames,
            Consumer<String> messageConsumer) {
        this.subscriptionFrames.clear();
        subscriptionFrames.forEach(frame -> this.subscriptionFrames.put(subscriptionKey(frame), frame));
        connect(websocketUrl, messageConsumer, 0);
    }

    private void connect(
            URI websocketUrl,
            Consumer<String> messageConsumer,
            int reconnectAttempt) {
        List<KisRealtimeSubscriptionFrame> frames = currentSubscriptionFrames();
        log.info(
                "Connecting KIS realtime websocket url={} subscriptionFrameCount={} reconnectAttempt={}",
                websocketUrl,
                frames.size(),
                reconnectAttempt);
        webSocketClient.execute(new Handler(websocketUrl, messageConsumer), websocketUrl.toString())
                .whenComplete((session, exception) -> {
                    if (exception != null) {
                        log.warn("KIS realtime websocket connection failed: {}", rootCauseMessage(exception));
                        scheduleReconnect(websocketUrl, messageConsumer, reconnectAttempt + 1);
                    } else {
                        log.info("KIS realtime websocket connected sessionId={}", session.getId());
                    }
                });
    }

    @Override
    public void send(List<KisRealtimeSubscriptionFrame> subscriptionFrames) {
        if (subscriptionFrames == null || subscriptionFrames.isEmpty()) {
            return;
        }
        subscriptionFrames.forEach(frame -> this.subscriptionFrames.put(subscriptionKey(frame), frame));
        WebSocketSession openSession = session.get();
        if (openSession == null || !openSession.isOpen()) {
            log.info("KIS realtime websocket dynamic subscription queued frameCount={}", subscriptionFrames.size());
            return;
        }
        sendFrames(openSession, subscriptionFrames);
    }

    @PreDestroy
    public void shutdown() {
        reconnectExecutor.shutdownNow();
    }

    private class Handler extends TextWebSocketHandler {

        private final URI websocketUrl;
        private final Consumer<String> messageConsumer;
        private final AtomicBoolean reconnectScheduled = new AtomicBoolean(false);

        private Handler(
                URI websocketUrl,
                Consumer<String> messageConsumer) {
            this.websocketUrl = websocketUrl;
            this.messageConsumer = messageConsumer;
        }

        @Override
        public void afterConnectionEstablished(WebSocketSession session) throws Exception {
            StandardKisRealtimeWebSocketConnection.this.session.set(session);
            List<KisRealtimeSubscriptionFrame> frames = currentSubscriptionFrames();
            sendFrames(session, frames);
            log.info(
                    "KIS realtime websocket subscription frames sent sessionId={} subscriptionFrameCount={}",
                    session.getId(),
                    frames.size());
        }

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) {
            String payload = message.getPayload();
            logKisMessage(session, payload);
            if (respondToPingPong(session, payload)) {
                return;
            }
            messageConsumer.accept(payload);
        }

        @Override
        public void handleTransportError(WebSocketSession session, Throwable exception) {
            log.warn("KIS realtime websocket transport error sessionId={} error={}",
                    session == null ? "" : session.getId(),
                    exception.toString());
            scheduleReconnectOnce();
        }

        @Override
        public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
            StandardKisRealtimeWebSocketConnection.this.session.compareAndSet(session, null);
            log.warn("KIS realtime websocket closed sessionId={} status={}", session.getId(), status);
            if (!CloseStatus.NORMAL.equals(status)) {
                scheduleReconnectOnce();
            }
        }

        private void scheduleReconnectOnce() {
            if (reconnectScheduled.compareAndSet(false, true)) {
                scheduleReconnect(websocketUrl, messageConsumer, 1);
            }
        }

        private boolean respondToPingPong(WebSocketSession session, String payload) {
            if (payload == null || !payload.startsWith("{")) {
                return false;
            }
            try {
                JsonNode root = objectMapper.readTree(payload);
                String trId = root.path("header").path("tr_id").asText("");
                if (!"PINGPONG".equals(trId)) {
                    return false;
                }
                session.sendMessage(new PongMessage(ByteBuffer.wrap(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
                return true;
            } catch (Exception exception) {
                log.warn("KIS realtime websocket pingpong response failed sessionId={} error={}",
                        session.getId(),
                        exception.toString());
                return false;
            }
        }
    }

    private void scheduleReconnect(
            URI websocketUrl,
            Consumer<String> messageConsumer,
            int reconnectAttempt) {
        long delaySeconds = reconnectAttempt > MAX_FAST_RECONNECT_ATTEMPTS
                ? GATEWAY_COOLDOWN_SECONDS
                : Math.min(1L << Math.min(reconnectAttempt - 1, 5), MAX_RECONNECT_DELAY_SECONDS);
        log.info(
                "Scheduling KIS realtime websocket reconnect delay={}s reconnectAttempt={} subscriptionFrameCount={}",
                delaySeconds,
                reconnectAttempt,
                subscriptionFrames.size());
        reconnectExecutor.schedule(
                () -> connect(websocketUrl, messageConsumer, reconnectAttempt),
                delaySeconds,
                TimeUnit.SECONDS);
    }

    private List<KisRealtimeSubscriptionFrame> currentSubscriptionFrames() {
        return new ArrayList<>(subscriptionFrames.values());
    }

    private void sendFrames(WebSocketSession session, List<KisRealtimeSubscriptionFrame> frames) {
        for (KisRealtimeSubscriptionFrame frame : frames) {
            try {
                session.sendMessage(new TextMessage(serialize(frame)));
                Thread.sleep(SUBSCRIPTION_FRAME_DELAY_MILLIS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("KIS realtime subscription send interrupted", exception);
            } catch (Exception exception) {
                throw new IllegalStateException("KIS realtime subscription send failed", exception);
            }
        }
    }

    private String subscriptionKey(KisRealtimeSubscriptionFrame frame) {
        return frame.body().input().trId() + ":" + frame.body().input().trKey();
    }

    private void logKisMessage(WebSocketSession session, String payload) {
        if (payload == null || payload.isBlank()) {
            return;
        }
        if (payload.startsWith("{")) {
            log.info("KIS realtime websocket control message sessionId={} payload={}",
                    session.getId(),
                    abbreviate(payload));
            return;
        }
        log.debug("KIS realtime websocket tick message received sessionId={} length={}",
                session.getId(),
                payload.length());
    }

    private String abbreviate(String payload) {
        int maxLength = 500;
        return payload.length() <= maxLength ? payload : payload.substring(0, maxLength) + "...";
    }

    private String serialize(KisRealtimeSubscriptionFrame frame) throws JsonProcessingException {
        return objectMapper.writeValueAsString(frame);
    }

    private String rootCauseMessage(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor.getCause() != null && cursor.getCause() != cursor) {
            cursor = cursor.getCause();
        }
        return cursor.toString();
    }

    private static StandardWebSocketClient standardWebSocketClient() {
        WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        container.setDefaultMaxTextMessageBufferSize(KIS_MESSAGE_BUFFER_SIZE_BYTES);
        container.setDefaultMaxBinaryMessageBufferSize(KIS_MESSAGE_BUFFER_SIZE_BYTES);
        return new StandardWebSocketClient(container);
    }
}

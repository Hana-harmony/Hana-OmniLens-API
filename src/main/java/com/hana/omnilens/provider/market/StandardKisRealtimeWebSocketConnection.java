package com.hana.omnilens.provider.market;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
    private static final int RECONNECT_JITTER_BOUND_SECONDS = 5;
    private static final long SUBSCRIPTION_FRAME_DELAY_MILLIS = 120;

    private final ObjectMapper objectMapper;
    private final StandardWebSocketClient webSocketClient;
    private final ScheduledExecutorService reconnectExecutor;
    private final Map<String, KisRealtimeSubscriptionFrame> dynamicSubscriptions = new ConcurrentHashMap<>();
    private final Set<String> sentSubscriptionKeys = ConcurrentHashMap.newKeySet();
    private final AtomicReference<WebSocketSession> currentSession = new AtomicReference<>();

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
        connect(websocketUrl, subscriptionFrames, messageConsumer, 0);
    }

    @Override
    public void subscribe(List<KisRealtimeSubscriptionFrame> subscriptionFrames) {
        for (KisRealtimeSubscriptionFrame frame : subscriptionFrames) {
            dynamicSubscriptions.put(subscriptionKey(frame), frame);
        }
        WebSocketSession session = currentSession.get();
        if (session == null || !session.isOpen()) {
            return;
        }
        sendFrames(session, subscriptionFrames);
    }

    @Override
    public void unsubscribe(List<KisRealtimeSubscriptionFrame> subscriptionFrames) {
        for (KisRealtimeSubscriptionFrame frame : subscriptionFrames) {
            dynamicSubscriptions.remove(subscriptionKey(frame));
            sentSubscriptionKeys.remove(subscriptionKey(frame));
        }
        WebSocketSession session = currentSession.get();
        if (session == null || !session.isOpen()) {
            return;
        }
        sendFrames(session, subscriptionFrames);
    }

    private void connect(
            URI websocketUrl,
            List<KisRealtimeSubscriptionFrame> subscriptionFrames,
            Consumer<String> messageConsumer,
            int reconnectAttempt) {
        log.info(
                "Connecting KIS realtime websocket url={} subscriptionFrameCount={} reconnectAttempt={}",
                websocketUrl,
                subscriptionFrames.size(),
                reconnectAttempt);
        webSocketClient.execute(new Handler(websocketUrl, subscriptionFrames, messageConsumer), websocketUrl.toString())
                .whenComplete((session, exception) -> {
                    if (exception != null) {
                        log.warn("KIS realtime websocket connection failed: {}", rootCauseMessage(exception));
                        scheduleReconnect(websocketUrl, subscriptionFrames, messageConsumer, reconnectAttempt + 1);
                    } else {
                        log.info("KIS realtime websocket connected sessionId={}", session.getId());
                    }
                });
    }

    @PreDestroy
    public void shutdown() {
        reconnectExecutor.shutdownNow();
    }

    private class Handler extends TextWebSocketHandler {

        private final URI websocketUrl;
        private final List<KisRealtimeSubscriptionFrame> subscriptionFrames;
        private final Consumer<String> messageConsumer;
        private final AtomicBoolean reconnectScheduled = new AtomicBoolean(false);

        private Handler(
                URI websocketUrl,
                List<KisRealtimeSubscriptionFrame> subscriptionFrames,
                Consumer<String> messageConsumer) {
            this.websocketUrl = websocketUrl;
            this.subscriptionFrames = subscriptionFrames;
            this.messageConsumer = messageConsumer;
        }

        @Override
        public void afterConnectionEstablished(WebSocketSession session) throws Exception {
            currentSession.set(session);
            sendFrames(session, activeSubscriptionFrames(subscriptionFrames));
            log.info(
                    "KIS realtime websocket subscription frames sent sessionId={} subscriptionFrameCount={}",
                    session.getId(),
                    activeSubscriptionFrames(subscriptionFrames).size());
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
            currentSession.compareAndSet(session, null);
            sentSubscriptionKeys.clear();
            log.warn("KIS realtime websocket closed sessionId={} status={}", session.getId(), status);
            if (!CloseStatus.NORMAL.equals(status)) {
                scheduleReconnectOnce();
            }
        }

        private void scheduleReconnectOnce() {
            if (reconnectScheduled.compareAndSet(false, true)) {
                scheduleReconnect(websocketUrl, subscriptionFrames, messageConsumer, 1);
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

    private List<KisRealtimeSubscriptionFrame> activeSubscriptionFrames(List<KisRealtimeSubscriptionFrame> baseFrames) {
        List<KisRealtimeSubscriptionFrame> frames = new ArrayList<>(baseFrames);
        frames.addAll(dynamicSubscriptions.values());
        return frames;
    }

    private void sendFrames(WebSocketSession session, List<KisRealtimeSubscriptionFrame> frames) {
        for (KisRealtimeSubscriptionFrame frame : frames) {
            try {
                if (KisRealtimeSubscriptionType.SUBSCRIBE.code().equals(frame.header().trType())
                        && !sentSubscriptionKeys.add(subscriptionKey(frame))) {
                    continue;
                }
                session.sendMessage(new TextMessage(serialize(frame)));
                Thread.sleep(SUBSCRIPTION_FRAME_DELAY_MILLIS);
            } catch (Exception exception) {
                log.warn("KIS realtime websocket subscription frame send failed sessionId={} trId={} trKey={} error={}",
                        session.getId(),
                        frame.body().input().trId(),
                        frame.body().input().trKey(),
                        exception.toString());
                throw new IllegalStateException("KIS realtime subscription frame send failed", exception);
            }
        }
    }

    private String subscriptionKey(KisRealtimeSubscriptionFrame frame) {
        return frame.body().input().trId() + ":" + frame.body().input().trKey();
    }

    private void scheduleReconnect(
            URI websocketUrl,
            List<KisRealtimeSubscriptionFrame> subscriptionFrames,
            Consumer<String> messageConsumer,
            int reconnectAttempt) {
        long delaySeconds = reconnectDelaySeconds(reconnectAttempt);
        log.info(
                "Scheduling KIS realtime websocket reconnect delay={}s reconnectAttempt={} subscriptionFrameCount={}",
                delaySeconds,
                reconnectAttempt,
                subscriptionFrames.size());
        reconnectExecutor.schedule(
                () -> connect(websocketUrl, subscriptionFrames, messageConsumer, reconnectAttempt),
                delaySeconds,
                TimeUnit.SECONDS);
    }

    static long reconnectDelaySeconds(int reconnectAttempt) {
        long exponentialDelaySeconds = Math.min(
                1L << Math.min(Math.max(reconnectAttempt - 1, 0), 5),
                MAX_RECONNECT_DELAY_SECONDS);
        long jitterBoundSeconds = Math.min(RECONNECT_JITTER_BOUND_SECONDS, exponentialDelaySeconds);
        long jitterSeconds = ThreadLocalRandom.current().nextLong(jitterBoundSeconds + 1);
        return Math.min(MAX_RECONNECT_DELAY_SECONDS, exponentialDelaySeconds + jitterSeconds);
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

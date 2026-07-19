package com.hana.omniconnect.market.stream;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hana.omniconnect.market.application.MarketDataService;
import com.hana.omniconnect.market.domain.MarketQuote;

@Service
public class MarketQuoteStreamingService {

    private static final int CACHED_REPLAY_LIMIT = 100;

    private final MarketDataService marketDataService;
    private final ObjectMapper objectMapper;
    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();
    private final AtomicLong publishedCount = new AtomicLong();
    private final AtomicLong failedDeliveryCount = new AtomicLong();
    private final AtomicReference<Instant> lastMarketDataTime = new AtomicReference<>();

    public MarketQuoteStreamingService(MarketDataService marketDataService, ObjectMapper objectMapper) {
        this.marketDataService = marketDataService;
        this.objectMapper = objectMapper;
    }

    void register(WebSocketSession session) {
        sessions.add(session);
    }

    void unregister(WebSocketSession session) {
        sessions.remove(session);
    }

    public MarketQuoteStreamStats stats() {
        return new MarketQuoteStreamStats(
                sessions.size(),
                publishedCount.get(),
                failedDeliveryCount.get(),
                lastMarketDataTime.get());
    }

    public void publishTick(String stockCode, String currency) {
        publish(marketDataService.getQuote(stockCode, currency, null));
    }

    void replay(WebSocketSession session, String currency) {
        marketDataService.getRealtimeCachedQuotes(null, null, currency, null, CACHED_REPLAY_LIMIT)
                .forEach(quote -> send(session, quote));
    }

    void replay(WebSocketSession session, String currency, List<String> stockCodes) {
        marketDataService.getRealtimeCachedQuotes(stockCodes, null, currency, null, Math.max(stockCodes.size(), 1))
                .forEach(quote -> send(session, quote));
    }

    private void publish(MarketQuote quote) {
        String payload = writePayload(quote);
        for (WebSocketSession session : sessions) {
            sendPayload(session, payload);
        }
        publishedCount.incrementAndGet();
        lastMarketDataTime.accumulateAndGet(quote.marketDataTime(), this::latest);
    }

    private void send(WebSocketSession session, MarketQuote quote) {
        sendPayload(session, writePayload(quote));
        lastMarketDataTime.accumulateAndGet(quote.marketDataTime(), this::latest);
    }

    private String writePayload(MarketQuote quote) {
        try {
            return objectMapper.writeValueAsString(quote);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize market quote stream payload", exception);
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
            failedDeliveryCount.incrementAndGet();
            sessions.remove(session);
        }
    }

    private Instant latest(Instant current, Instant candidate) {
        if (current == null) {
            return candidate;
        }
        return current.compareTo(candidate) >= 0 ? current : candidate;
    }
}

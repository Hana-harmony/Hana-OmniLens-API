package com.hana.omnilens.market.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hana.omnilens.market.application.ForeignOwnershipSnapshotCache;
import com.hana.omnilens.market.application.MarketDataService;
import com.hana.omnilens.market.application.RealtimeMarketDataIngestionService;
import com.hana.omnilens.market.application.StockMasterRepository;
import com.hana.omnilens.provider.market.ForeignOwnershipSnapshot;
import com.hana.omnilens.provider.market.KisCurrentPriceClient;
import com.hana.omnilens.provider.market.KisCurrentPriceSnapshot;
import com.hana.omnilens.provider.market.KisRealtimeTransaction;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "omnilens.security.api-key-enabled=true",
                "omnilens.security.api-key-sha256=4c806362b613f7496abf284146efd31da90e4b16169fe001841ca17290f427c4",
                "omnilens.security.rate-limit.enabled=false",
                "omnilens.alert.dedupe.mode=in-memory",
                "management.health.redis.enabled=false"
        })
class MarketQuoteWebSocketContractTest {

    @LocalServerPort
    private int port;

    @Autowired
    private RealtimeMarketDataIngestionService ingestionService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MarketDataService marketDataService;

    @Autowired
    private ForeignOwnershipSnapshotCache foreignOwnershipSnapshotCache;

    @Autowired
    private StockMasterRepository stockMasterRepository;

    @MockitoBean
    private KisCurrentPriceClient kisCurrentPriceClient;

    @BeforeEach
    void setUpProviderCaches() {
        marketDataService.updateExchangeRate("USD", new BigDecimal("0.00072"));
        when(kisCurrentPriceClient.findCurrentPrice(anyString()))
                .thenAnswer(invocation -> Optional.of(kisSnapshot(invocation.getArgument(0))));
        stockMasterRepository.findAll(100)
                .forEach(stock -> foreignOwnershipSnapshotCache.put(new ForeignOwnershipSnapshot(
                        stock.stockCode(),
                        3_642_091_300L,
                        new BigDecimal("54.19"),
                        6_720_000_000L,
                        new BigDecimal("54.19"),
                        LocalDate.of(2025, 6, 4))));
    }

    @Test
    void rawWebSocketSubscriberReceivesKisRealtimeQuoteTick() throws Exception {
        BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        WebSocketSession session = connect(messages);

        ingestionService.ingestKisMessage(kisFrame(KisRealtimeTransaction.TRADE, tradePayload()));

        Map<String, Object> payload = readPayload(messages);
        assertThat(payload.get("stockCode")).isEqualTo("005930");
        assertThat(payload.get("stockNameEn")).isEqualTo("Samsung Electronics");
        assertThat(payload.get("currentPriceKrw")).isEqualTo(81500);
        assertThat(payload.get("localCurrency")).isEqualTo("USD");
        assertThat((String) payload.get("fxRateSource")).isEqualTo("EXCHANGE_RATE_CACHE");
        assertThat(payload.get("fxStale")).isInstanceOf(Boolean.class);
        assertThat((String) payload.get("source")).startsWith("KIS_WEBSOCKET_TRADE");

        session.close();
    }

    @Test
    void replayRequestReturnsCurrentQuoteSnapshot() throws Exception {
        BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        WebSocketSession session = connect(messages);
        ingestionService.ingestKisMessage(kisFrame(KisRealtimeTransaction.TRADE, tradePayload()));

        session.sendMessage(new TextMessage("""
                {"type":"QUOTE_STREAM_REPLAY","currency":"USD","after":"2026-06-18T06:00:00Z"}
                """));

        Map<String, Object> payload = readPayload(messages);
        assertThat(payload.get("stockCode")).isNotNull();
        assertThat(payload.get("localCurrency")).isEqualTo("USD");
        assertThat(payload).containsKeys("fxRate", "fxRateTime", "fxRateSource", "fxStale");

        session.close();
    }

    private WebSocketSession connect(BlockingQueue<String> messages) throws Exception {
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.set("X-HANA-OMNILENS-API-KEY", "test-api-key");
        return new StandardWebSocketClient()
                .execute(new TextWebSocketHandler() {
                    @Override
                    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
                        messages.add(message.getPayload());
                    }
                }, headers, URI.create("ws://localhost:" + port + "/ws/market/quotes"))
                .get(5, TimeUnit.SECONDS);
    }

    private Map<String, Object> readPayload(BlockingQueue<String> messages) throws Exception {
        String message = messages.poll(5, TimeUnit.SECONDS);
        assertThat(message).isNotNull();
        return objectMapper.readValue(message, new TypeReference<>() {
        });
    }

    private String kisFrame(KisRealtimeTransaction transaction, String payload) {
        return "0|" + transaction.trId() + "|001|" + payload;
    }

    private String tradePayload() {
        ArrayList<String> fields = new ArrayList<>(Collections.nCopies(46, "0"));
        fields.set(0, "005930");
        fields.set(1, "093000");
        fields.set(2, "81500");
        fields.set(5, "1.92");
        fields.set(10, "81600");
        fields.set(11, "81400");
        fields.set(12, "1200");
        fields.set(13, "16200000");
        fields.set(33, "20250604");
        return String.join("^", fields);
    }

    private KisCurrentPriceSnapshot kisSnapshot(String stockCode) {
        return new KisCurrentPriceSnapshot(
                stockCode,
                "삼성전자",
                new BigDecimal("78500"),
                new BigDecimal("1.42"),
                12_193_000L,
                3_642_091_300L,
                new BigDecimal("54.19"),
                6_720_000_000L,
                new BigDecimal("54.19"));
    }
}

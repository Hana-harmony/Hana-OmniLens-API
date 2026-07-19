package com.hana.omniconnect.alert.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hana.omniconnect.alert.domain.AlertEvent;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "omni-connect.security.rate-limit.enabled=false",
                "omni-connect.alert.dedupe.mode=in-memory",
                "management.health.redis.enabled=false"
        })
class AlertWebSocketContractTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void deletePersistedContractState() {
        jdbcTemplate.update("DELETE FROM partner_api_credential");
        com.hana.omniconnect.support.PartnerCredentialTestData.replace(
                jdbcTemplate, "partner-a", "test-api-key");
        jdbcTemplate.update("DELETE FROM alert_event");
    }

    @Test
	void partnerSubscriberReceivesPublishedAlertEvent() throws Exception {
        StompSession session = connect("test-api-key");

        BlockingQueue<AlertEvent> partnerEvents = new LinkedBlockingQueue<>();
		session.subscribe("/topic/partners/partner-a/alerts", queueingHandler(partnerEvents));
        TimeUnit.MILLISECONDS.sleep(300);

        var response = publishAlert("test-api-key", "partner-a");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        AlertEvent partnerEvent = partnerEvents.poll(5, TimeUnit.SECONDS);

		assertThat(partnerEvent).isNotNull();
        assertThat(partnerEvent.partnerId()).isEqualTo("partner-a");
        assertThat(partnerEvent.stockCode()).isEqualTo("005930");
        assertThat(partnerEvent.eventTags()).containsExactly("EARNINGS");
        assertThat(partnerEvent.importance()).isEqualTo("HIGH");
        assertThat(partnerEvent.marketImpactImportance()).isEqualTo("MEDIUM");
        assertThat(partnerEvent.marketImpactScore()).isEqualTo(0.42);
        assertThat(partnerEvent.marketImpactConfidence()).isEqualTo(0.81);
        assertThat(partnerEvent.glossaryTerms()).isEmpty();
        assertThat(partnerEvent.translationQualityFlags()).isEmpty();
        assertThat(partnerEvent.duplicateKey()).isEqualTo("manual-duplicate");
        assertThat(partnerEvent.modelVersion()).isEqualTo("manual-publisher");
        assertThat(partnerEvent.eventConfidence()).isEqualTo(0.91);
        assertThat(partnerEvent.stockMatchConfidence()).isEqualTo(1.0);

        if (session.isConnected()) {
            session.disconnect();
        }
    }

    @Test
    void rawAlertEventStreamPublishesExchangeBackendPayload() throws Exception {
        BlockingQueue<String> rawEvents = new LinkedBlockingQueue<>();
        WebSocketSession session = new StandardWebSocketClient()
                .execute(new TextWebSocketHandler() {
                    @Override
                    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
                        rawEvents.add(message.getPayload());
                    }
                }, rawHeaders("test-api-key"), java.net.URI.create("ws://localhost:" + port + "/ws/alerts/events"))
                .get(5, TimeUnit.SECONDS);

        var response = publishAlert("test-api-key", "partner-a");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        String payload = rawEvents.poll(5, TimeUnit.SECONDS);
        assertThat(payload).isNotNull();
        Map<?, ?> event = objectMapper.readValue(payload, Map.class);
        assertThat((String) event.get("eventId")).isNotBlank();
        assertThat(event.get("idempotencyKey")).isEqualTo("manual-duplicate");
        assertThat(event.get("title")).isEqualTo("Samsung Electronics earnings improve");
        assertThat(event.get("stockCode")).isEqualTo("005930");
        assertThat(event.get("riskLevel")).isEqualTo("HIGH");
        assertThat(event.get("importance")).isEqualTo("HIGH");
        assertThat(event.get("marketImpactImportance")).isEqualTo("MEDIUM");
        assertThat(event.get("marketImpactScore")).isEqualTo(0.42);
        assertThat(event.get("marketImpactConfidence")).isEqualTo(0.81);
        assertThat(event.get("modelVersion")).isEqualTo("manual-publisher");

        if (session.isOpen()) {
            session.close();
        }
    }

    @Test
    void partnerCredentialSubscribersReceivePartnerScopedTopics() throws Exception {
        insertPartnerCredential("partner-a", "partner-a-api-key");
        StompSession session = connect("partner-a-api-key");

        BlockingQueue<AlertEvent> partnerEvents = new LinkedBlockingQueue<>();
        BlockingQueue<AlertEvent> partnerStockEvents = new LinkedBlockingQueue<>();
        session.subscribe("/topic/partners/partner-a/alerts", queueingHandler(partnerEvents));
        session.subscribe("/topic/partners/partner-a/stocks/005930/alerts", queueingHandler(partnerStockEvents));
        TimeUnit.MILLISECONDS.sleep(300);

        var response = publishAlert("partner-a-api-key", "partner-a");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        AlertEvent partnerEvent = partnerEvents.poll(5, TimeUnit.SECONDS);
        AlertEvent partnerStockEvent = partnerStockEvents.poll(5, TimeUnit.SECONDS);

        assertThat(partnerEvent).isNotNull();
        assertThat(partnerStockEvent).isNotNull();
        assertThat(partnerStockEvent.alertId()).isEqualTo(partnerEvent.alertId());

        if (session.isConnected()) {
            session.disconnect();
        }
    }

    @Test
    void partnerCredentialCannotReceiveGlobalStockTopic() throws Exception {
        insertPartnerCredential("partner-a", "partner-a-api-key");
        StompSession session = connect("partner-a-api-key");

        BlockingQueue<AlertEvent> globalStockEvents = new LinkedBlockingQueue<>();
        session.subscribe("/topic/stocks/005930/alerts", queueingHandler(globalStockEvents));
        TimeUnit.MILLISECONDS.sleep(300);

        var response = publishAlert("partner-a-api-key", "partner-a");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(globalStockEvents.poll(1, TimeUnit.SECONDS)).isNull();

        if (session.isConnected()) {
            session.disconnect();
        }
    }

    private StompSession connect(String apiKey) throws Exception {
        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        MappingJackson2MessageConverter messageConverter = new MappingJackson2MessageConverter();
        messageConverter.setObjectMapper(objectMapper);
        stompClient.setMessageConverter(messageConverter);
        WebSocketHttpHeaders handshakeHeaders = new WebSocketHttpHeaders();
        handshakeHeaders.set("X-HANA-OMNI-CONNECT-API-KEY", apiKey);
        return stompClient.connectAsync(
                        "ws://localhost:" + port + "/ws/alerts",
                        handshakeHeaders,
                        new StompSessionHandlerAdapter() {
                        })
                .get(5, TimeUnit.SECONDS);
    }

    private WebSocketHttpHeaders rawHeaders(String apiKey) {
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.set("X-HANA-OMNI-CONNECT-API-KEY", apiKey);
        return headers;
    }

    private HttpEntity<Map<String, Object>> request(String apiKey, String partnerId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-HANA-OMNI-CONNECT-API-KEY", apiKey);
        return new HttpEntity<>(alertPayload(partnerId), headers);
    }

    private org.springframework.http.ResponseEntity<AlertEvent> publishAlert(String apiKey, String partnerId) {
        return restTemplate.exchange(
                "/api/v1/alerts/events",
                HttpMethod.POST,
                request(apiKey, partnerId),
                AlertEvent.class);
    }

    private StompFrameHandler queueingHandler(BlockingQueue<AlertEvent> events) {
        return new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return AlertEvent.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                events.add((AlertEvent) payload);
            }
        };
    }

    private Map<String, Object> alertPayload(String partnerId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("partnerId", partnerId);
        payload.put("stockCode", "005930");
        payload.put("stockName", "삼성전자");
        payload.put("sourceType", "NEWS");
        payload.put("originalTitle", "삼성전자 실적 개선");
        payload.put("translatedTitle", "Samsung Electronics earnings improve");
        payload.put("summary", "반도체 수요 회복으로 실적 개선 기대");
        payload.put("originalUrl", "https://example.com/news/contract-test");
        payload.put("publishedAt", Instant.parse("2026-06-04T00:00:00Z").toString());
        payload.put("eventTags", List.of("EARNINGS"));
        payload.put("sentiment", "POSITIVE");
        payload.put("importance", "HIGH");
        payload.put("marketImpactImportance", "MEDIUM");
        payload.put("marketImpactScore", 0.42);
        payload.put("marketImpactConfidence", 0.81);
        payload.put("relatedStocks", List.of("005930"));
        payload.put("holderTarget", true);
        payload.put("watchlistTarget", true);
        payload.put("glossaryTerms", List.of(Map.of(
                "sourceTerm", "실적",
                "normalizedTerm", "실적",
                "englishTerm", "earnings",
                "category", "event")));
        payload.put("translationQualityFlags", List.of("FINANCIAL_GLOSSARY_APPLIED"));
        payload.put("duplicateKey", "manual-duplicate");
        payload.put("modelVersion", "manual-publisher");
        payload.put("eventConfidence", 0.91);
        payload.put("sentimentConfidence", 0.89);
        payload.put("importanceConfidence", 0.93);
        payload.put("stockMatchConfidence", 1.0);
        return payload;
    }

    private void insertPartnerCredential(String partnerId, String apiKey) throws Exception {
        jdbcTemplate.update(
                """
                INSERT INTO partner_api_credential (api_key_sha256, partner_id, active)
                VALUES (?, ?, TRUE)
                """,
                sha256Hex(apiKey),
                partnerId);
    }

    private String sha256Hex(String rawValue) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(rawValue.getBytes(StandardCharsets.UTF_8)));
    }
}

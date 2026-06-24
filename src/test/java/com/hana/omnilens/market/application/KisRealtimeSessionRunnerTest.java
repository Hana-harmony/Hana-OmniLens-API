package com.hana.omnilens.market.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import com.hana.omnilens.config.ExternalProviderProperties;
import com.hana.omnilens.config.KisRealtimeProperties;
import com.hana.omnilens.market.infra.InMemoryStockMasterRepository;
import com.hana.omnilens.provider.market.KisRealtimeMessageParser;
import com.hana.omnilens.provider.market.KisRealtimeApprovalKeyProvider;
import com.hana.omnilens.provider.market.KisRealtimeSubscriptionFrame;
import com.hana.omnilens.provider.market.KisRealtimeSubscriptionFrameFactory;
import com.hana.omnilens.provider.market.KisRealtimeTransaction;
import com.hana.omnilens.provider.market.KisRealtimeWebSocketConnection;
import com.hana.omnilens.market.stream.MarketQuoteStreamingService;

class KisRealtimeSessionRunnerTest {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");
    private static final Clock REGULAR_MARKET_CLOCK =
            Clock.fixed(Instant.parse("2026-06-24T01:00:00Z"), KOREA_ZONE);
    private static final Clock AFTER_HOURS_CLOCK =
            Clock.fixed(Instant.parse("2026-06-24T08:10:00Z"), KOREA_ZONE);

    @Test
    void startDoesNothingWhenDisabled() {
        FakeConnection connection = new FakeConnection();
        KisRealtimeSessionRunner runner = newRunner(
                new KisRealtimeProperties(false, List.of("005930")),
                connection,
                new InMemoryRealtimeMarketDataCache());

        runner.start();

        assertThat(connection.connected).isFalse();
    }

    @Test
    void emptyStockCodesSubscribeStockMasterUniverse() {
        FakeConnection connection = new FakeConnection();
        KisRealtimeSessionRunner runner = new KisRealtimeSessionRunner(
                new KisRealtimeProperties(true, List.of("", " ")),
                new ExternalProviderProperties(null, null, null, null, null, null),
                new KisRealtimeSubscriptionFrameFactory(),
                connection,
                new RealtimeMarketDataIngestionService(
                        new KisRealtimeMessageParser(),
                        new InMemoryRealtimeMarketDataCache(),
                        mock(MarketQuoteStreamingService.class)),
                approvalKeyProvider(),
                new InMemoryStockMasterRepository(),
                REGULAR_MARKET_CLOCK);

        runner.start();

        assertThat(connection.connected).isTrue();
        assertThat(connection.frames).hasSize(6);
        assertThat(connection.frames).extracting(frame -> frame.body().input().trId())
                .containsOnly("H0STCNT0");
    }

    @Test
    void startConnectsWithTradeAndOrderBookSubscriptions() {
        FakeConnection connection = new FakeConnection();
        KisRealtimeSessionRunner runner = newRunner(
                new KisRealtimeProperties(true, List.of("005930", "000660"), 2500, 40, true),
                connection,
                new InMemoryRealtimeMarketDataCache());

        runner.start();

        assertThat(connection.connected).isTrue();
        assertThat(connection.websocketUrl).isEqualTo(URI.create("wss://kis.example/ws"));
        assertThat(connection.frames).hasSize(4);
        assertThat(connection.frames).extracting(frame -> frame.header().approvalKey())
                .containsOnly("approval-key");
        assertThat(connection.frames).extracting(frame -> frame.body().input().trId())
                .containsExactly("H0STCNT0", "H0STASP0", "H0STCNT0", "H0STASP0");
    }

    @Test
    void startConnectsWithAfterHoursTradeAndOrderBookSubscriptions() {
        FakeConnection connection = new FakeConnection();
        KisRealtimeSessionRunner runner = newRunner(
                new KisRealtimeProperties(true, List.of("005930"), 2500, 40, true, true),
                connection,
                new InMemoryRealtimeMarketDataCache(),
                approvalKeyProvider(),
                AFTER_HOURS_CLOCK);

        runner.start();

        assertThat(connection.connected).isTrue();
        assertThat(connection.frames).hasSize(2);
        assertThat(connection.frames).extracting(frame -> frame.body().input().trId())
                .containsExactly("H0STOUP0", "H0STOAA0");
    }

    @Test
    void regularMarketSessionUsesRegularTradeAndOrderBookSubscriptionsEvenWhenAfterHoursEnabled() {
        FakeConnection connection = new FakeConnection();
        KisRealtimeSessionRunner runner = newRunner(
                new KisRealtimeProperties(true, List.of("005930"), 2500, 40, true, true),
                connection,
                new InMemoryRealtimeMarketDataCache(),
                approvalKeyProvider(),
                REGULAR_MARKET_CLOCK);

        runner.start();

        assertThat(connection.connected).isTrue();
        assertThat(connection.frames).hasSize(2);
        assertThat(connection.frames).extracting(frame -> frame.body().input().trId())
                .containsExactly("H0STCNT0", "H0STASP0");
    }

    @Test
    void startCapsSubscriptionsToKisAppKeyLimit() {
        FakeConnection connection = new FakeConnection();
        KisRealtimeSessionRunner runner = new KisRealtimeSessionRunner(
                new KisRealtimeProperties(true, List.of("005930", "000660", "035420"), 2500, 2, false),
                externalProviderProperties(),
                new KisRealtimeSubscriptionFrameFactory(),
                connection,
                new RealtimeMarketDataIngestionService(
                        new KisRealtimeMessageParser(),
                        new InMemoryRealtimeMarketDataCache(),
                        mock(MarketQuoteStreamingService.class)),
                approvalKeyProvider(),
                new InMemoryStockMasterRepository(),
                REGULAR_MARKET_CLOCK);

        runner.start();

        assertThat(connection.connectCount).isEqualTo(1);
        assertThat(connection.allFrames).hasSize(2);
        assertThat(connection.frameBatches).extracting(List::size).containsExactly(2);
    }

    @Test
    void startDoesNotFailApplicationWhenApprovalKeyProviderFails() {
        FakeConnection connection = new FakeConnection();
        KisRealtimeApprovalKeyProvider approvalKeyProvider = mock(KisRealtimeApprovalKeyProvider.class);
        when(approvalKeyProvider.approvalKey()).thenThrow(new IllegalStateException("approval timeout"));
        KisRealtimeSessionRunner runner = newRunner(
                new KisRealtimeProperties(true, List.of("005930")),
                connection,
                new InMemoryRealtimeMarketDataCache(),
                approvalKeyProvider);

        runner.start();

        assertThat(connection.connected).isFalse();
    }

    @Test
    void startWiresIncomingMessagesToRealtimeCache() {
        FakeConnection connection = new FakeConnection();
        RealtimeMarketDataCache cache = new InMemoryRealtimeMarketDataCache();
        KisRealtimeSessionRunner runner = newRunner(
                new KisRealtimeProperties(true, List.of("005930")),
                connection,
                cache);

        runner.start();
        connection.emit(kisFrame(KisRealtimeTransaction.TRADE, tradePayload()));

        assertThat(cache.latestTrade("005930")).isPresent();
        assertThat(cache.latestTrade("005930").orElseThrow().currentPriceKrw()).isEqualByComparingTo("81500");
    }

    private KisRealtimeSessionRunner newRunner(
            KisRealtimeProperties properties,
            FakeConnection connection,
            RealtimeMarketDataCache cache) {
        return new KisRealtimeSessionRunner(
                properties,
                externalProviderProperties(),
                new KisRealtimeSubscriptionFrameFactory(),
                connection,
                new RealtimeMarketDataIngestionService(
                        new KisRealtimeMessageParser(),
                        cache,
                        mock(MarketQuoteStreamingService.class)),
                approvalKeyProvider(),
                new InMemoryStockMasterRepository(),
                REGULAR_MARKET_CLOCK);
    }

    private KisRealtimeSessionRunner newRunner(
            KisRealtimeProperties properties,
            FakeConnection connection,
            RealtimeMarketDataCache cache,
            KisRealtimeApprovalKeyProvider approvalKeyProvider) {
        return new KisRealtimeSessionRunner(
                properties,
                externalProviderProperties(),
                new KisRealtimeSubscriptionFrameFactory(),
                connection,
                new RealtimeMarketDataIngestionService(
                        new KisRealtimeMessageParser(),
                        cache,
                        mock(MarketQuoteStreamingService.class)),
                approvalKeyProvider,
                new InMemoryStockMasterRepository(),
                REGULAR_MARKET_CLOCK);
    }

    private KisRealtimeSessionRunner newRunner(
            KisRealtimeProperties properties,
            FakeConnection connection,
            RealtimeMarketDataCache cache,
            KisRealtimeApprovalKeyProvider approvalKeyProvider,
            Clock clock) {
        return new KisRealtimeSessionRunner(
                properties,
                externalProviderProperties(),
                new KisRealtimeSubscriptionFrameFactory(),
                connection,
                new RealtimeMarketDataIngestionService(
                        new KisRealtimeMessageParser(),
                        cache,
                        mock(MarketQuoteStreamingService.class)),
                approvalKeyProvider,
                new InMemoryStockMasterRepository(),
                clock);
    }

    private KisRealtimeApprovalKeyProvider approvalKeyProvider() {
        KisRealtimeApprovalKeyProvider provider = mock(KisRealtimeApprovalKeyProvider.class);
        when(provider.approvalKey()).thenReturn("approval-key");
        return provider;
    }

    private ExternalProviderProperties externalProviderProperties() {
        return new ExternalProviderProperties(
                null,
                null,
                null,
                null,
                new ExternalProviderProperties.Kis(
                        URI.create("https://kis.example"),
                        URI.create("wss://kis.example/ws"),
                        "00000000",
                        "app-key",
                        "app-secret",
                        "access-token",
                        "approval-key"),
                null);
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

    private static class FakeConnection implements KisRealtimeWebSocketConnection {

        private boolean connected;
        private int connectCount;
        private URI websocketUrl;
        private List<KisRealtimeSubscriptionFrame> frames = List.of();
        private List<KisRealtimeSubscriptionFrame> allFrames = new ArrayList<>();
        private List<List<KisRealtimeSubscriptionFrame>> frameBatches = new ArrayList<>();
        private Consumer<String> messageConsumer = message -> {
        };

        @Override
        public void connect(
                URI websocketUrl,
                List<KisRealtimeSubscriptionFrame> subscriptionFrames,
                Consumer<String> messageConsumer) {
            this.connected = true;
            this.connectCount += 1;
            this.websocketUrl = websocketUrl;
            this.frames = subscriptionFrames;
            this.allFrames.addAll(subscriptionFrames);
            this.frameBatches.add(subscriptionFrames);
            this.messageConsumer = messageConsumer;
        }

        private void emit(String rawMessage) {
            messageConsumer.accept(rawMessage);
        }
    }
}

package com.hana.omnilens.market.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.TaskScheduler;

import com.hana.omnilens.config.ExternalProviderProperties;
import com.hana.omnilens.config.KisRealtimeProperties;
import com.hana.omnilens.provider.market.KisRealtimeApprovalKeyProvider;
import com.hana.omnilens.provider.market.KisRealtimeSubscriptionFrame;
import com.hana.omnilens.provider.market.KisRealtimeSubscriptionFrameFactory;
import com.hana.omnilens.provider.market.KisRealtimeWebSocketConnection;

class KisRealtimeIndexSessionRunnerTest {

    @Test
    void startConnectsRealKisIndexSessionWhenPrimaryProviderIsVirtualTrading() {
        FakeConnection connection = new FakeConnection();
        ExternalProviderProperties.Kis realKis = realKis();
        KisRealtimeIndexSessionRunner runner = new KisRealtimeIndexSessionRunner(
                new KisRealtimeProperties(
                        true,
                        List.of("005930"),
                        List.of("0001", "1001"),
                        2500,
                        40,
                        false,
                        false),
                externalProviderProperties(vtsKis(), realKis),
                new KisRealtimeSubscriptionFrameFactory(),
                mock(RealtimeMarketDataIngestionService.class),
                Optional.of(realKis),
                approvalKeyProvider(),
                connection);

        runner.start();

        assertThat(connection.connected).isTrue();
        assertThat(connection.websocketUrl).isEqualTo(URI.create("ws://ops.koreainvestment.com:21000"));
        assertThat(connection.frames).hasSize(2);
        assertThat(connection.frames).extracting(frame -> frame.body().input().trId())
                .containsOnly("H0UPCNT0");
        assertThat(connection.frames).extracting(frame -> frame.body().input().trKey())
                .containsExactly("0001", "1001");
    }

    @Test
    void startDoesNotConnectWhenRealKisIndexCredentialIsMissing() {
        FakeConnection connection = new FakeConnection();
        KisRealtimeIndexSessionRunner runner = new KisRealtimeIndexSessionRunner(
                new KisRealtimeProperties(
                        true,
                        List.of("005930"),
                        List.of("0001"),
                        2500,
                        40,
                        false,
                        false),
                externalProviderProperties(vtsKis(), null),
                new KisRealtimeSubscriptionFrameFactory(),
                mock(RealtimeMarketDataIngestionService.class),
                Optional.empty(),
                null,
                connection);

        runner.start();

        assertThat(connection.connected).isFalse();
    }

    @Test
    void startRetriesRealIndexApprovalKeyAfterTransientFailure() {
        FakeConnection connection = new FakeConnection();
        TaskScheduler taskScheduler = mock(TaskScheduler.class);
        KisRealtimeApprovalKeyProvider approvalKeyProvider = mock(KisRealtimeApprovalKeyProvider.class);
        when(approvalKeyProvider.approvalKey())
                .thenThrow(new IllegalStateException("approval timeout"))
                .thenReturn("real-approval-key");
        ExternalProviderProperties.Kis realKis = realKis();
        KisRealtimeIndexSessionRunner runner = new KisRealtimeIndexSessionRunner(
                new KisRealtimeProperties(
                        true,
                        List.of("005930"),
                        List.of("0001"),
                        2500,
                        40,
                        false,
                        false),
                externalProviderProperties(vtsKis(), realKis),
                new KisRealtimeSubscriptionFrameFactory(),
                mock(RealtimeMarketDataIngestionService.class),
                Optional.of(realKis),
                approvalKeyProvider,
                connection,
                taskScheduler,
                Clock.fixed(Instant.parse("2026-07-13T06:00:00Z"), ZoneOffset.UTC));

        runner.start();
        ArgumentCaptor<Runnable> retryTask = ArgumentCaptor.forClass(Runnable.class);
        verify(taskScheduler).schedule(retryTask.capture(), any(Instant.class));
        retryTask.getValue().run();

        assertThat(connection.connected).isTrue();
    }

    private KisRealtimeApprovalKeyProvider approvalKeyProvider() {
        KisRealtimeApprovalKeyProvider provider = mock(KisRealtimeApprovalKeyProvider.class);
        when(provider.approvalKey()).thenReturn("real-approval-key");
        return provider;
    }

    private ExternalProviderProperties externalProviderProperties(
            ExternalProviderProperties.Kis kis,
            ExternalProviderProperties.Kis realKis) {
        return new ExternalProviderProperties(
                null,
                null,
                null,
                null,
                kis,
                realKis);
    }

    private ExternalProviderProperties.Kis realKis() {
        return new ExternalProviderProperties.Kis(
                URI.create("https://openapi.koreainvestment.com:9443"),
                URI.create("ws://ops.koreainvestment.com:21000"),
                "00000000",
                "real-app-key",
                "real-app-secret",
                "",
                "");
    }

    private ExternalProviderProperties.Kis vtsKis() {
        return new ExternalProviderProperties.Kis(
                URI.create("https://openapivts.koreainvestment.com:29443"),
                URI.create("ws://ops.koreainvestment.com:31000"),
                "00000000",
                "vts-app-key",
                "vts-app-secret",
                "",
                "");
    }

    private static class FakeConnection implements KisRealtimeWebSocketConnection {

        private boolean connected;
        private URI websocketUrl;
        private List<KisRealtimeSubscriptionFrame> frames = List.of();

        @Override
        public void connect(
                URI websocketUrl,
                List<KisRealtimeSubscriptionFrame> subscriptionFrames,
                Consumer<String> messageConsumer) {
            this.connected = true;
            this.websocketUrl = websocketUrl;
            this.frames = subscriptionFrames;
        }

        @Override
        public void subscribe(List<KisRealtimeSubscriptionFrame> subscriptionFrames) {
        }
    }
}

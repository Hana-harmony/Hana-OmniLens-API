package com.hana.omnilens.provider.market;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hana.omnilens.config.ExternalProviderProperties;
import com.hana.omnilens.config.KisRealtimeProperties;
import com.hana.omnilens.market.application.RealtimeMarketDataIngestionService;
import com.hana.omnilens.market.infra.InMemoryStockMasterRepository;
import com.hana.omnilens.provider.ExternalProviderResiliencePolicy;

class OnDemandKisRealtimeSubscriptionServiceTest {

    @Test
    void unsubscribeRegularKeepsDefaultUniversePinned() {
        FakeConnection connection = new FakeConnection();
        OnDemandKisRealtimeSubscriptionService service = service(
                connection,
                new KisRealtimeProperties(true, List.of("005930", "000660")));

        KisRealtimeSubscriptionRequestResult result = service.unsubscribeRegular("005930");

        assertThat(result.status()).isEqualTo("UNCHANGED");
        assertThat(connection.unsubscribedFrames).isEmpty();
    }

    @Test
    void unsubscribeRegularSendsFrameForNonPinnedStock() {
        FakeConnection connection = new FakeConnection();
        OnDemandKisRealtimeSubscriptionService service = service(
                connection,
                new KisRealtimeProperties(true, List.of("005930")));

        KisRealtimeSubscriptionRequestResult result = service.unsubscribeRegular("000660");

        assertThat(result.status()).isEqualTo("UNSUBSCRIBED");
        assertThat(connection.unsubscribedFrames).hasSize(1);
        assertThat(connection.unsubscribedFrames.get(0).body().input().trId()).isEqualTo("H0STCNT0");
        assertThat(connection.unsubscribedFrames.get(0).body().input().trKey()).isEqualTo("000660");
    }

    private OnDemandKisRealtimeSubscriptionService service(
            FakeConnection connection,
            KisRealtimeProperties kisRealtimeProperties) {
        return new OnDemandKisRealtimeSubscriptionService(
                connection,
                approvalKeyProvider(),
                new KisRealtimeSubscriptionFrameFactory(),
                kisRealtimeProperties,
                new ExternalProviderProperties(null, null, null, null, null, null),
                mock(ExternalProviderResiliencePolicy.class),
                RestClient.builder(),
                new ObjectMapper(),
                mock(RealtimeMarketDataIngestionService.class),
                new InMemoryStockMasterRepository());
    }

    private KisRealtimeApprovalKeyProvider approvalKeyProvider() {
        return new KisRealtimeApprovalKeyProvider(
                RestClient.builder(),
                new ExternalProviderProperties.Kis(
                        URI.create("https://kis.example"),
                        URI.create("wss://kis.example/ws"),
                        "",
                        "",
                        "",
                        "",
                        "approval-key"),
                mock(ExternalProviderResiliencePolicy.class));
    }

    private static class FakeConnection implements KisRealtimeWebSocketConnection {
        private final List<KisRealtimeSubscriptionFrame> unsubscribedFrames = new ArrayList<>();

        @Override
        public void connect(
                URI websocketUrl,
                List<KisRealtimeSubscriptionFrame> subscriptionFrames,
                Consumer<String> messageConsumer) {
        }

        @Override
        public void unsubscribe(List<KisRealtimeSubscriptionFrame> subscriptionFrames) {
            unsubscribedFrames.addAll(subscriptionFrames);
        }
    }
}

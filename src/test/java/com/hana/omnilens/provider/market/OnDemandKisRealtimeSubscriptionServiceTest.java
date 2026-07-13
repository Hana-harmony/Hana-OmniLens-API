package com.hana.omnilens.provider.market;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hana.omnilens.config.ExternalProviderProperties;
import com.hana.omnilens.market.application.KisRealtimeDynamicSubscriptionResult;
import com.hana.omnilens.market.application.KisRealtimeSessionRunner;
import com.hana.omnilens.market.application.KisRealtimeSessionRunner.KisRealtimeUnsubscribeResult;
import com.hana.omnilens.market.application.RealtimeMarketDataIngestionService;
import com.hana.omnilens.market.infra.InMemoryStockMasterRepository;
import com.hana.omnilens.provider.ExternalProviderResiliencePolicy;

class OnDemandKisRealtimeSubscriptionServiceTest {

    @Test
    void subscribeRegularReportsProviderLimitRotation() {
        KisRealtimeSessionRunner runner = mock(KisRealtimeSessionRunner.class);
        when(runner.subscribeStockCodes(List.of("000660"))).thenReturn(new KisRealtimeDynamicSubscriptionResult(
                true,
                40,
                40,
                List.of("000660"),
                List.of(),
                List.of("035420"),
                List.of(),
                List.of()));

        KisRealtimeSubscriptionRequestResult result = service(runner).subscribeRegular("000660");

        assertThat(result.status()).isEqualTo("SUBSCRIBED");
        assertThat(result.message()).contains("rotated");
    }

    @Test
    void unsubscribeRegularKeepsDefaultUniversePinned() {
        KisRealtimeSessionRunner runner = mock(KisRealtimeSessionRunner.class);
        when(runner.unsubscribeStockCode("005930")).thenReturn(KisRealtimeUnsubscribeResult.PINNED);

        KisRealtimeSubscriptionRequestResult result = service(runner).unsubscribeRegular("005930");

        assertThat(result.status()).isEqualTo("UNCHANGED");
    }

    @Test
    void unsubscribeRegularReleasesDynamicStock() {
        KisRealtimeSessionRunner runner = mock(KisRealtimeSessionRunner.class);
        when(runner.unsubscribeStockCode("000660")).thenReturn(KisRealtimeUnsubscribeResult.UNSUBSCRIBED);

        KisRealtimeSubscriptionRequestResult result = service(runner).unsubscribeRegular("000660");

        assertThat(result.status()).isEqualTo("UNSUBSCRIBED");
    }

    private OnDemandKisRealtimeSubscriptionService service(KisRealtimeSessionRunner runner) {
        return new OnDemandKisRealtimeSubscriptionService(
                runner,
                new KisRealtimeSubscriptionFrameFactory(),
                new ExternalProviderProperties(null, null, null, null, null, null),
                mock(ExternalProviderResiliencePolicy.class),
                RestClient.builder(),
                new ObjectMapper(),
                mock(RealtimeMarketDataIngestionService.class),
                new InMemoryStockMasterRepository());
    }
}

package com.hana.omniconnect.market.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClientException;

import com.hana.omniconnect.market.domain.StockSummary;
import com.hana.omniconnect.provider.ai.HannahAiGlobalPeerMatchClient;

class GlobalPeerMatchServiceTest {

    @Test
    void aiFailureDoesNotReturnFabricatedPeerData() {
        StockMasterRepository repository = mock(StockMasterRepository.class);
        HannahAiGlobalPeerMatchClient client = mock(HannahAiGlobalPeerMatchClient.class);
        when(repository.findByCode("035720")).thenReturn(Optional.of(new StockSummary(
                "035720",
                "카카오",
                "Kakao",
                "KOSPI",
                "KR7035720002",
                "00258801")));
        when(client.match(any())).thenThrow(new RestClientException("AI unavailable"));

        GlobalPeerMatchService service = new GlobalPeerMatchService(repository, client);

        assertThatThrownBy(() -> service.match("035720"))
                .isInstanceOf(MarketDataUnavailableException.class)
                .hasMessageContaining("035720");
    }
}

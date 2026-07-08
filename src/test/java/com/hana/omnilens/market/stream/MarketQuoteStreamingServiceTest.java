package com.hana.omnilens.market.stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hana.omnilens.market.application.MarketDataService;

class MarketQuoteStreamingServiceTest {

    @Test
    void unscopedReplayUsesRealtimeCacheInsteadOfBulkQuoteLookup() {
        MarketDataService marketDataService = mock(MarketDataService.class);
        MarketQuoteStreamingService service = new MarketQuoteStreamingService(
                marketDataService,
                new ObjectMapper());
        WebSocketSession session = mock(WebSocketSession.class);
        when(marketDataService.getRealtimeCachedQuotes(null, null, "USD", null, 100))
                .thenReturn(List.of());

        service.replay(session, "USD");

        verify(marketDataService).getRealtimeCachedQuotes(null, null, "USD", null, 100);
        verify(marketDataService, never()).getQuotes(any(), any(), eq("USD"), any(), anyInt());
    }

    @Test
    void scopedReplayUsesOnlyRequestedRealtimeCache() {
        MarketDataService marketDataService = mock(MarketDataService.class);
        MarketQuoteStreamingService service = new MarketQuoteStreamingService(
                marketDataService,
                new ObjectMapper());
        WebSocketSession session = mock(WebSocketSession.class);
        List<String> stockCodes = List.of("005930", "000660");
        when(marketDataService.getRealtimeCachedQuotes(stockCodes, null, "USD", null, 2))
                .thenReturn(List.of());

        service.replay(session, "USD", stockCodes);

        verify(marketDataService).getRealtimeCachedQuotes(stockCodes, null, "USD", null, 2);
        verify(marketDataService, never()).getQuotes(any(), any(), eq("USD"), any(), anyInt());
    }
}

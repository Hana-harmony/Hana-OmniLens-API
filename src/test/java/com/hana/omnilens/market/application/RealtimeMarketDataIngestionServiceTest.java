package com.hana.omnilens.market.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.Collections;

import org.junit.jupiter.api.Test;

import com.hana.omnilens.provider.market.KisRealtimeMessageParser;
import com.hana.omnilens.provider.market.KisRealtimeTransaction;
import com.hana.omnilens.market.stream.MarketQuoteStreamingService;

class RealtimeMarketDataIngestionServiceTest {

    private final RealtimeMarketDataCache cache = new InMemoryRealtimeMarketDataCache();
    private final MarketQuoteStreamingService streamingService = mock(MarketQuoteStreamingService.class);
    private final RealtimeMarketDataIngestionService service =
            new RealtimeMarketDataIngestionService(new KisRealtimeMessageParser(), cache, streamingService);

    @Test
    void ingestKisMessageStoresTradeTick() {
        RealtimeMarketDataIngestionResult result = service.ingestKisMessage(kisFrame(
                KisRealtimeTransaction.TRADE,
                tradePayload()));

        assertThat(result.type()).isEqualTo(RealtimeMarketDataIngestionResult.Type.TRADE);
        assertThat(result.stockCode()).isEqualTo("005930");
        assertThat(cache.latestTrade("005930")).isPresent();
        assertThat(cache.latestTrade("005930").orElseThrow().currentPriceKrw()).isEqualByComparingTo("81500");
        verify(streamingService).publishTick("005930", "USD");
    }

    @Test
    void ingestKisMessageStoresOrderBookSnapshot() {
        RealtimeMarketDataIngestionResult result = service.ingestKisMessage(kisFrame(
                KisRealtimeTransaction.ORDERBOOK,
                orderBookPayload()));

        assertThat(result.type()).isEqualTo(RealtimeMarketDataIngestionResult.Type.ORDERBOOK);
        assertThat(result.stockCode()).isEqualTo("005930");
        assertThat(cache.latestOrderBook("005930")).isPresent();
        assertThat(cache.latestOrderBook("005930").orElseThrow().asks().get(0).priceKrw())
                .isEqualByComparingTo("81600");
    }

    @Test
    void ingestKisMessageIgnoresUnsupportedMessage() {
        RealtimeMarketDataIngestionResult result = service.ingestKisMessage("""
                {"header":{"tr_id":"PINGPONG"}}
                """);

        assertThat(result.type()).isEqualTo(RealtimeMarketDataIngestionResult.Type.IGNORED);
        assertThat(result.stockCode()).isEmpty();
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

    private String orderBookPayload() {
        ArrayList<String> fields = new ArrayList<>(Collections.nCopies(58, "0"));
        fields.set(0, "005930");
        fields.set(1, "093001");
        for (int index = 0; index < 10; index++) {
            fields.set(3 + index, String.valueOf(81600 + (index * 100)));
            fields.set(13 + index, String.valueOf(81400 - (index * 100)));
            fields.set(23 + index, String.valueOf(1200 + index));
            fields.set(33 + index, String.valueOf(1800 + index));
        }
        fields.set(53, "16200000");
        return String.join("^", fields);
    }
}

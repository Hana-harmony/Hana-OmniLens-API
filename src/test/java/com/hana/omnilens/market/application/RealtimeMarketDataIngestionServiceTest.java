package com.hana.omnilens.market.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.hana.omnilens.provider.market.KisRealtimeMessageParser;
import com.hana.omnilens.provider.market.KisRealtimeTransaction;
import com.hana.omnilens.market.domain.MarketIntradayRealtimeTick;
import com.hana.omnilens.market.domain.StockSummary;
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
    void ingestKisMessageStoresRealtimeTradeAsMinuteCandle() {
        RealtimeMarketDataCache localCache = new InMemoryRealtimeMarketDataCache();
        MarketQuoteStreamingService localStreamingService = mock(MarketQuoteStreamingService.class);
        MarketIntradayPriceRepository intradayPriceRepository = mock(MarketIntradayPriceRepository.class);
        StockMasterRepository stockMasterRepository = mock(StockMasterRepository.class);
        RealtimeMarketDataIngestionService realtimeService = new RealtimeMarketDataIngestionService(
                new KisRealtimeMessageParser(),
                localCache,
                localStreamingService,
                null,
                intradayPriceRepository,
                stockMasterRepository,
                Clock.fixed(Instant.parse("2025-06-04T00:30:10Z"), ZoneId.of("Asia/Seoul")));
        when(stockMasterRepository.findByCode("005930")).thenReturn(Optional.of(new StockSummary(
                "005930",
                "삼성전자",
                "Samsung Electronics",
                "KOSPI",
                "KR7005930003",
                "00126380")));

        realtimeService.ingestKisMessage(kisFrame(KisRealtimeTransaction.TRADE, tradePayload()));

        ArgumentCaptor<MarketIntradayRealtimeTick> captor =
                ArgumentCaptor.forClass(MarketIntradayRealtimeTick.class);
        verify(intradayPriceRepository).recordRealtimeTick(captor.capture());
        MarketIntradayRealtimeTick saved = captor.getValue();
        assertThat(saved.stockCode()).isEqualTo("005930");
        assertThat(saved.bucketStart()).isEqualTo(LocalDateTime.of(2025, 6, 4, 9, 30));
        assertThat(saved.market()).isEqualTo("KOSPI");
        assertThat(saved.priceKrw()).isEqualByComparingTo("81500");
        assertThat(saved.executionVolume()).isEqualTo(1200L);
        assertThat(saved.tradingValueKrw()).isEqualByComparingTo(new BigDecimal("97800000"));
        assertThat(saved.source()).isEqualTo("KIS_REALTIME_TRADE");
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

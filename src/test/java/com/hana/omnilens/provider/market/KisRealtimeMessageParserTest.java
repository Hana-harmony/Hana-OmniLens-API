package com.hana.omnilens.provider.market;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class KisRealtimeMessageParserTest {

    private final KisRealtimeMessageParser parser = new KisRealtimeMessageParser();

    @Test
    void parseTradeTickMapsKisTradePayload() {
        Optional<KisRealtimeTradeTick> tick = parser.parseTradeTick(kisFrame(
                KisRealtimeTransaction.TRADE,
                tradePayload()));

        assertThat(tick).isPresent();
        assertThat(tick.orElseThrow().stockCode()).isEqualTo("005930");
        assertThat(tick.orElseThrow().tradeTime()).isEqualTo("093000");
        assertThat(tick.orElseThrow().currentPriceKrw()).isEqualByComparingTo("81500");
        assertThat(tick.orElseThrow().changeRate()).isEqualByComparingTo("1.92");
        assertThat(tick.orElseThrow().askPrice1Krw()).isEqualByComparingTo("81600");
        assertThat(tick.orElseThrow().bidPrice1Krw()).isEqualByComparingTo("81400");
        assertThat(tick.orElseThrow().executionVolume()).isEqualTo(1200L);
        assertThat(tick.orElseThrow().accumulatedVolume()).isEqualTo(16_200_000L);
        assertThat(tick.orElseThrow().businessDate()).isEqualTo(LocalDate.of(2025, 6, 4));
    }

    @Test
    void parseOrderBookMapsKisOrderBookPayload() {
        Optional<KisRealtimeOrderBookSnapshot> orderBook = parser.parseOrderBook(kisFrame(
                KisRealtimeTransaction.ORDERBOOK,
                orderBookPayload()));

        assertThat(orderBook).isPresent();
        assertThat(orderBook.orElseThrow().stockCode()).isEqualTo("005930");
        assertThat(orderBook.orElseThrow().marketTime()).isEqualTo("093001");
        assertThat(orderBook.orElseThrow().asks()).hasSize(10);
        assertThat(orderBook.orElseThrow().asks().get(0).priceKrw()).isEqualByComparingTo("81600");
        assertThat(orderBook.orElseThrow().asks().get(0).quantity()).isEqualTo(1200L);
        assertThat(orderBook.orElseThrow().bids().get(0).priceKrw()).isEqualByComparingTo("81400");
        assertThat(orderBook.orElseThrow().bids().get(0).quantity()).isEqualTo(1800L);
        assertThat(orderBook.orElseThrow().accumulatedVolume()).isEqualTo(16_200_000L);
    }

    @Test
    void parseIgnoresOtherTransactions() {
        assertThat(parser.parseTradeTick(kisFrame(KisRealtimeTransaction.ORDERBOOK, orderBookPayload()))).isEmpty();
        assertThat(parser.parseOrderBook(kisFrame(KisRealtimeTransaction.TRADE, tradePayload()))).isEmpty();
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

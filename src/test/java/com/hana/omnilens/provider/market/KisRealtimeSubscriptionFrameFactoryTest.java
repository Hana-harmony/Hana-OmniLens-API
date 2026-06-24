package com.hana.omnilens.provider.market;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class KisRealtimeSubscriptionFrameFactoryTest {

    private final KisRealtimeSubscriptionFrameFactory factory = new KisRealtimeSubscriptionFrameFactory();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void createBuildsKisTradeSubscriptionFrame() throws Exception {
        KisRealtimeSubscriptionFrame frame = factory.create(
                "approval-key",
                KisRealtimeTransaction.TRADE,
                KisRealtimeSubscriptionType.SUBSCRIBE,
                "005930");

        JsonNode json = objectMapper.valueToTree(frame);

        assertThat(json.at("/header/approval_key").asText()).isEqualTo("approval-key");
        assertThat(json.at("/header/tr_type").asText()).isEqualTo("1");
        assertThat(json.at("/header/custtype").asText()).isEqualTo("P");
        assertThat(json.at("/header/content-type").asText()).isEqualTo("utf-8");
        assertThat(json.at("/body/input/tr_id").asText()).isEqualTo("H0STCNT0");
        assertThat(json.at("/body/input/tr_key").asText()).isEqualTo("005930");
    }

    @Test
    void createBuildsKisOrderBookUnsubscribeFrame() {
        KisRealtimeSubscriptionFrame frame = factory.create(
                "approval-key",
                KisRealtimeTransaction.ORDERBOOK,
                KisRealtimeSubscriptionType.UNSUBSCRIBE,
                "000660");

        assertThat(frame.header().trType()).isEqualTo("2");
        assertThat(frame.body().input().trId()).isEqualTo("H0STASP0");
        assertThat(frame.body().input().trKey()).isEqualTo("000660");
    }

    @Test
    void createRejectsInvalidStockCode() {
        assertThatThrownBy(() -> factory.create(
                "approval-key",
                KisRealtimeTransaction.TRADE,
                KisRealtimeSubscriptionType.SUBSCRIBE,
                "ABCDEF"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("stockCode");
    }
}

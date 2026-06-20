package com.hana.omnilens.docs;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "omnilens.security.api-key-enabled=true",
        "omnilens.security.api-key-sha256=4c806362b613f7496abf284146efd31da90e4b16169fe001841ca17290f427c4",
        "omnilens.alert.dedupe.mode=in-memory",
        "management.health.redis.enabled=false"
})
@AutoConfigureMockMvc
class OpenApiDocumentTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void servesOpenApiDocumentBehindPartnerApiKey() throws Exception {
        mockMvc.perform(get("/openapi.yaml")
                        .header("X-HANA-OMNILENS-API-KEY", "test-api-key"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("openapi: 3.1.0")))
                .andExpect(content().string(containsString("/api/v1/market/stocks/{stockCode}")))
                .andExpect(content().string(containsString("/api/v1/market/stocks/{stockCode}/quote")))
                .andExpect(content().string(containsString("/api/v1/market/stocks/{stockCode}/detail")))
                .andExpect(content().string(containsString("/api/v1/market/quotes")))
                .andExpect(content().string(containsString("/api/v1/market/stocks/{stockCode}/history")))
                .andExpect(content().string(containsString("MarketDailyPrice")))
                .andExpect(content().string(containsString("StockSummaryApiResponse")))
                .andExpect(content().string(containsString("ExchangeRateApiResponse")))
                .andExpect(content().string(containsString("MarketQuoteApiResponse")))
                .andExpect(content().string(containsString("StockDetailApiResponse")))
                .andExpect(content().string(containsString("PartnerWatchlistApiResponse")))
                .andExpect(content().string(containsString("/api/v1/security/partners/{partnerId}/credentials/rotate")))
                .andExpect(content().string(containsString("PartnerCredentialRotationApiResponse")))
                .andExpect(content().string(containsString("raw key exactly once")))
                .andExpect(content().string(containsString("AlertEventApiResponse")))
                .andExpect(content().string(containsString("AlertCollectPublishApiResponse")))
                .andExpect(content().string(containsString("fxRateSource")))
                .andExpect(content().string(containsString("KIS_OPEN_API+KIS_FOREIGN_OWNERSHIP_CACHE")))
                .andExpect(content().string(containsString("/api/v1/alerts/collect-and-publish")))
                .andExpect(content().string(containsString("/ws/alerts")))
                .andExpect(content().string(containsString("/topic/partners/{partnerId}/alerts")))
                .andExpect(content().string(containsString("/topic/partners/{partnerId}/stocks/{stockCode}/alerts")))
                .andExpect(content().string(containsString("/ws/market/quotes")))
                .andExpect(content().string(containsString("QUOTE_STREAM_REPLAY")))
                .andExpect(content().string(containsString("type: mutualTLS")))
                .andExpect(content().string(containsString("X-HANA-OMNILENS-API-KEY")));
    }

    @Test
    void protectsOpenApiDocumentWhenApiKeyIsMissing() throws Exception {
        mockMvc.perform(get("/openapi.yaml"))
                .andExpect(status().isUnauthorized());
    }
}

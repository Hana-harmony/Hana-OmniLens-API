package com.hana.omnilens.market.api;

import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "omnilens.security.api-key-enabled=true",
        "omnilens.security.api-key-sha256=4c806362b613f7496abf284146efd31da90e4b16169fe001841ca17290f427c4",
        "omnilens.providers.public-data.service-key=",
        "omnilens.alert.dedupe.mode=in-memory",
        "management.health.redis.enabled=false"
})
@AutoConfigureMockMvc
class MarketDataControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void stockDetailReturnsSeededStockMasterRow() throws Exception {
        mockMvc.perform(get("/api/v1/market/stocks/086790")
                        .header("X-HANA-OMNILENS-API-KEY", "test-api-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stockCode", equalTo("086790")))
                .andExpect(jsonPath("$.stockName", equalTo("하나금융지주")))
                .andExpect(jsonPath("$.stockNameEn", equalTo("Hana Financial Group")))
                .andExpect(jsonPath("$.market", equalTo("KOSPI")))
                .andExpect(jsonPath("$.isinCode", equalTo("KR7086790003")));
    }

    @Test
    void stockDetailReturnsNotFoundForUnsupportedStockCode() throws Exception {
        mockMvc.perform(get("/api/v1/market/stocks/999999")
                        .header("X-HANA-OMNILENS-API-KEY", "test-api-key"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type", equalTo("https://hana-omnilens-api/errors/stock-not-found")))
                .andExpect(jsonPath("$.title", equalTo("Stock not found")))
                .andExpect(jsonPath("$.stockCode", equalTo("999999")));
    }

    @Test
    void stockDetailRejectsInvalidStockCode() throws Exception {
        mockMvc.perform(get("/api/v1/market/stocks/ABCDEF")
                        .header("X-HANA-OMNILENS-API-KEY", "test-api-key"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type", equalTo("https://hana-omnilens-api/errors/validation")))
                .andExpect(jsonPath("$.title", equalTo("Invalid request")));
    }

    @Test
    void quoteApiReturnsStandardMarketPayload() throws Exception {
        mockMvc.perform(get("/api/v1/market/stocks/005930/quote")
                        .header("X-HANA-OMNILENS-API-KEY", "test-api-key")
                        .param("currency", "USD")
                        .param("fxRate", "0.00072"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stockCode", equalTo("005930")))
                .andExpect(jsonPath("$.baseCurrency", equalTo("KRW")))
                .andExpect(jsonPath("$.localCurrency", equalTo("USD")))
                .andExpect(jsonPath("$.source", equalTo("MOCK_MARKET_DATA")));
    }

    @Test
    void quoteApiRequiresApiKey() throws Exception {
        mockMvc.perform(get("/api/v1/market/stocks/005930/quote"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void quoteApiRejectsInvalidStockCode() throws Exception {
        mockMvc.perform(get("/api/v1/market/stocks/ABCDEF/quote")
                        .header("X-HANA-OMNILENS-API-KEY", "test-api-key"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type", equalTo("https://hana-omnilens-api/errors/validation")))
                .andExpect(jsonPath("$.title", equalTo("Invalid request")));
    }

    @Test
    void quoteApiRejectsInvalidCurrencyAndFxRate() throws Exception {
        mockMvc.perform(get("/api/v1/market/stocks/005930/quote")
                        .header("X-HANA-OMNILENS-API-KEY", "test-api-key")
                        .param("currency", "usd")
                        .param("fxRate", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type", equalTo("https://hana-omnilens-api/errors/validation")))
                .andExpect(jsonPath("$.title", equalTo("Invalid request")));
    }

    @Test
    void exchangeRateApiStoresPartnerRateForQuoteFallback() throws Exception {
        mockMvc.perform(put("/api/v1/market/exchange-rates/JPY")
                        .header("X-HANA-OMNILENS-API-KEY", "test-api-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fxRate\":0.11}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baseCurrency", equalTo("KRW")))
                .andExpect(jsonPath("$.localCurrency", equalTo("JPY")))
                .andExpect(jsonPath("$.fxRate", equalTo(0.11)));

        mockMvc.perform(get("/api/v1/market/stocks/005930/quote")
                        .header("X-HANA-OMNILENS-API-KEY", "test-api-key")
                        .param("currency", "JPY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.localCurrency", equalTo("JPY")))
                .andExpect(jsonPath("$.localCurrencyPrice", equalTo(8635.0)));
    }

    @Test
    void exchangeRateApiRejectsInvalidCurrencyAndRate() throws Exception {
        mockMvc.perform(put("/api/v1/market/exchange-rates/jpy")
                        .header("X-HANA-OMNILENS-API-KEY", "test-api-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fxRate\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type", equalTo("https://hana-omnilens-api/errors/validation")))
                .andExpect(jsonPath("$.title", equalTo("Invalid request")));
    }

    @Test
    void stockSearchRejectsEmptyQuery() throws Exception {
        mockMvc.perform(get("/api/v1/market/stocks/search")
                        .header("X-HANA-OMNILENS-API-KEY", "test-api-key")
                        .param("query", ""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void stockSearchReturnsSeededStockMasterRows() throws Exception {
        mockMvc.perform(get("/api/v1/market/stocks/search")
                        .header("X-HANA-OMNILENS-API-KEY", "test-api-key")
                        .param("query", "하나금융"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].stockCode", equalTo("086790")))
                .andExpect(jsonPath("$[0].stockName", equalTo("하나금융지주")))
                .andExpect(jsonPath("$[0].market", equalTo("KOSPI")));
    }
}

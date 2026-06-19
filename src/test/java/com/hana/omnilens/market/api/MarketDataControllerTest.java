package com.hana.omnilens.market.api;

import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.hana.omnilens.market.application.ForeignOwnershipRefreshResult;
import com.hana.omnilens.market.application.ForeignOwnershipRefreshService;
import com.hana.omnilens.market.application.MarketDailyPriceRepository;
import com.hana.omnilens.market.domain.MarketDailyPrice;
import com.hana.omnilens.provider.market.ForeignOwnershipSnapshot;

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

    @Autowired
    private MarketDailyPriceRepository marketDailyPriceRepository;

    @MockitoBean
    private ForeignOwnershipRefreshService foreignOwnershipRefreshService;

    @Test
    void stockDetailReturnsSeededStockMasterRow() throws Exception {
        mockMvc.perform(get("/api/v1/market/stocks/086790")
                        .header("X-HANA-OMNILENS-API-KEY", "test-api-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", equalTo(true)))
                .andExpect(jsonPath("$.status", equalTo(200)))
                .andExpect(jsonPath("$.code", equalTo("COMMON_000")))
                .andExpect(jsonPath("$.data.stockCode", equalTo("086790")))
                .andExpect(jsonPath("$.data.stockName", equalTo("하나금융지주")))
                .andExpect(jsonPath("$.data.stockNameEn", equalTo("Hana Financial Group")))
                .andExpect(jsonPath("$.data.market", equalTo("KOSPI")))
                .andExpect(jsonPath("$.data.isinCode", equalTo("KR7086790003")));
    }

    @Test
    void stockDetailReturnsNotFoundForUnsupportedStockCode() throws Exception {
        mockMvc.perform(get("/api/v1/market/stocks/999999")
                        .header("X-HANA-OMNILENS-API-KEY", "test-api-key"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success", equalTo(false)))
                .andExpect(jsonPath("$.code", equalTo("MARKET_001")))
                .andExpect(jsonPath("$.message", equalTo("Stock master row not found: 999999")));
    }

    @Test
    void stockDetailRejectsInvalidStockCode() throws Exception {
        mockMvc.perform(get("/api/v1/market/stocks/ABCDEF")
                        .header("X-HANA-OMNILENS-API-KEY", "test-api-key"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", equalTo(false)))
                .andExpect(jsonPath("$.code", equalTo("COMMON_002")));
    }

    @Test
    void quoteApiReturnsStandardMarketPayload() throws Exception {
        mockMvc.perform(get("/api/v1/market/stocks/005930/quote")
                        .header("X-HANA-OMNILENS-API-KEY", "test-api-key")
                        .param("currency", "USD")
                        .param("fxRate", "0.00072"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", equalTo(true)))
                .andExpect(jsonPath("$.status", equalTo(200)))
                .andExpect(jsonPath("$.code", equalTo("COMMON_000")))
                .andExpect(jsonPath("$.data.stockCode", equalTo("005930")))
                .andExpect(jsonPath("$.data.baseCurrency", equalTo("KRW")))
                .andExpect(jsonPath("$.data.localCurrency", equalTo("USD")))
                .andExpect(jsonPath("$.data.fxRate", equalTo(7.2E-4)))
                .andExpect(jsonPath("$.data.fxRateSource", equalTo("PARTNER_REQUEST")))
                .andExpect(jsonPath("$.data.fxStale", equalTo(false)))
                .andExpect(jsonPath("$.data.source", equalTo("MOCK_MARKET_DATA")));
    }

    @Test
    void stockDetailApiReturnsQuoteAndOrderabilityContract() throws Exception {
        mockMvc.perform(get("/api/v1/market/stocks/005930/detail")
                        .header("X-HANA-OMNILENS-API-KEY", "test-api-key")
                        .param("currency", "USD")
                        .param("fxRate", "0.00072"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", equalTo(true)))
                .andExpect(jsonPath("$.data.stockCode", equalTo("005930")))
                .andExpect(jsonPath("$.data.stockName", equalTo("삼성전자")))
                .andExpect(jsonPath("$.data.stockNameEn", equalTo("Samsung Electronics")))
                .andExpect(jsonPath("$.data.market", equalTo("KOSPI")))
                .andExpect(jsonPath("$.data.currentPriceKrw").exists())
                .andExpect(jsonPath("$.data.localCurrency", equalTo("USD")))
                .andExpect(jsonPath("$.data.localCurrencyPrice").exists())
                .andExpect(jsonPath("$.data.foreignOwnedQuantity").exists())
                .andExpect(jsonPath("$.data.foreignOwnershipRate").exists())
                .andExpect(jsonPath("$.data.predictedForeignOwnershipRateMin").exists())
                .andExpect(jsonPath("$.data.predictedForeignOwnershipRateMax").exists())
                .andExpect(jsonPath("$.data.predictedForeignLimitExhaustionRateMin").exists())
                .andExpect(jsonPath("$.data.predictedForeignLimitExhaustionRateMax").exists())
                .andExpect(jsonPath("$.data.viActive", equalTo(false)))
                .andExpect(jsonPath("$.data.singlePriceTrading", equalTo(false)))
                .andExpect(jsonPath("$.data.priceLimitState", equalTo("NORMAL")))
                .andExpect(jsonPath("$.data.tradingHalted", equalTo(false)))
                .andExpect(jsonPath("$.data.orderable", equalTo(true)));
    }

    @Test
    void bulkQuoteApiReturnsRequestedStocksInCommonEnvelope() throws Exception {
        mockMvc.perform(get("/api/v1/market/quotes")
                        .header("X-HANA-OMNILENS-API-KEY", "test-api-key")
                        .param("currency", "USD")
                        .param("fxRate", "0.00072")
                        .param("stockCodes", "005930", "000660"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", equalTo(true)))
                .andExpect(jsonPath("$.data[0].stockCode", equalTo("005930")))
                .andExpect(jsonPath("$.data[0].localCurrency", equalTo("USD")))
                .andExpect(jsonPath("$.data[0].fxRateSource", equalTo("PARTNER_REQUEST")))
                .andExpect(jsonPath("$.data[1].stockCode", equalTo("000660")));
    }

    @Test
    void allQuoteApiReturnsSeededStocksWithMarketFilter() throws Exception {
        mockMvc.perform(get("/api/v1/market/quotes")
                        .header("X-HANA-OMNILENS-API-KEY", "test-api-key")
                        .param("currency", "USD")
                        .param("fxRate", "0.00072")
                        .param("market", "KOSPI")
                        .param("limit", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()", equalTo(3)))
                .andExpect(jsonPath("$.data[0].market", equalTo("KOSPI")));
    }

    @Test
    void bulkQuoteApiRejectsInvalidStockCodeAndLimit() throws Exception {
        mockMvc.perform(get("/api/v1/market/quotes")
                        .header("X-HANA-OMNILENS-API-KEY", "test-api-key")
                        .param("stockCodes", "ABCDEF")
                        .param("limit", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", equalTo(false)))
                .andExpect(jsonPath("$.code", equalTo("COMMON_002")));
    }

    @Test
    void quoteApiRequiresApiKey() throws Exception {
        mockMvc.perform(get("/api/v1/market/stocks/005930/quote"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success", equalTo(false)))
                .andExpect(jsonPath("$.code", equalTo("AUTH_001")));
    }

    @Test
    void quoteApiRejectsInvalidStockCode() throws Exception {
        mockMvc.perform(get("/api/v1/market/stocks/ABCDEF/quote")
                        .header("X-HANA-OMNILENS-API-KEY", "test-api-key"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", equalTo(false)))
                .andExpect(jsonPath("$.code", equalTo("COMMON_002")));
    }

    @Test
    void quoteApiRejectsInvalidCurrencyAndFxRate() throws Exception {
        mockMvc.perform(get("/api/v1/market/stocks/005930/quote")
                        .header("X-HANA-OMNILENS-API-KEY", "test-api-key")
                        .param("currency", "usd")
                        .param("fxRate", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", equalTo(false)))
                .andExpect(jsonPath("$.code", equalTo("COMMON_002")));
    }

    @Test
    void orderabilityApiReturnsPartnerMockOrderBoundary() throws Exception {
        mockMvc.perform(get("/api/v1/market/stocks/005930/orderability")
                        .header("X-HANA-OMNILENS-API-KEY", "test-api-key")
                        .param("side", "BUY")
                        .param("quantity", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", equalTo(true)))
                .andExpect(jsonPath("$.status", equalTo(200)))
                .andExpect(jsonPath("$.data.stockCode", equalTo("005930")))
                .andExpect(jsonPath("$.data.market", equalTo("KOSPI")))
                .andExpect(jsonPath("$.data.side", equalTo("BUY")))
                .andExpect(jsonPath("$.data.quantity", equalTo(1)))
                .andExpect(jsonPath("$.data.orderable", equalTo(true)))
                .andExpect(jsonPath("$.data.foreignLimitExceeded", equalTo(false)))
                .andExpect(jsonPath("$.data.foreignOwnershipPrediction.minForeignLimitExhaustionRate").exists())
                .andExpect(jsonPath("$.data.foreignOwnershipPrediction.baseForeignLimitExhaustionRate").exists())
                .andExpect(jsonPath("$.data.foreignOwnershipPrediction.maxForeignLimitExhaustionRate").exists())
                .andExpect(jsonPath("$.data.foreignOwnershipPrediction.confidenceLevel").exists())
                .andExpect(jsonPath("$.data.viActive", equalTo(false)))
                .andExpect(jsonPath("$.data.singlePriceTrading", equalTo(false)))
                .andExpect(jsonPath("$.data.priceLimitState", equalTo("NORMAL")))
                .andExpect(jsonPath("$.data.tradingHalted", equalTo(false)));
    }

    @Test
    void orderabilityApiRejectsInvalidSideAndQuantity() throws Exception {
        mockMvc.perform(get("/api/v1/market/stocks/005930/orderability")
                        .header("X-HANA-OMNILENS-API-KEY", "test-api-key")
                        .param("side", "buy")
                        .param("quantity", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", equalTo(false)))
                .andExpect(jsonPath("$.code", equalTo("COMMON_002")));
    }

    @Test
    void exchangeRateApiStoresPartnerRateForQuoteFallback() throws Exception {
        mockMvc.perform(put("/api/v1/market/exchange-rates/JPY")
                        .header("X-HANA-OMNILENS-API-KEY", "test-api-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fxRate\":0.11}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", equalTo(true)))
                .andExpect(jsonPath("$.status", equalTo(200)))
                .andExpect(jsonPath("$.code", equalTo("COMMON_000")))
                .andExpect(jsonPath("$.data.baseCurrency", equalTo("KRW")))
                .andExpect(jsonPath("$.data.localCurrency", equalTo("JPY")))
                .andExpect(jsonPath("$.data.fxRate", equalTo(0.11)));

        mockMvc.perform(get("/api/v1/market/stocks/005930/quote")
                        .header("X-HANA-OMNILENS-API-KEY", "test-api-key")
                        .param("currency", "JPY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.localCurrency", equalTo("JPY")))
                .andExpect(jsonPath("$.data.localCurrencyPrice", equalTo(8635.0)));
    }

    @Test
    void historyApiReturnsStoredKrxDailyPricesInCommonEnvelope() throws Exception {
        marketDailyPriceRepository.upsertAll(List.of(new MarketDailyPrice(
                "005930",
                LocalDate.of(2025, 6, 4),
                "KOSPI",
                new BigDecimal("57900"),
                new BigDecimal("58900"),
                new BigDecimal("57500"),
                new BigDecimal("58700"),
                new BigDecimal("1.91"),
                19_123_456L,
                new BigDecimal("1122334455000"),
                new BigDecimal("58700"),
                "KRX_OPEN_API_DAILY_TRADE",
                Instant.parse("2025-06-04T07:00:00Z"))));

        mockMvc.perform(get("/api/v1/market/stocks/005930/history")
                        .header("X-HANA-OMNILENS-API-KEY", "test-api-key")
                        .param("from", "2025-06-01")
                        .param("to", "2025-06-05")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", equalTo(true)))
                .andExpect(jsonPath("$.data[0].stockCode", equalTo("005930")))
                .andExpect(jsonPath("$.data[0].tradeDate", equalTo("2025-06-04")))
                .andExpect(jsonPath("$.data[0].openPriceKrw", equalTo(57900.0)))
                .andExpect(jsonPath("$.data[0].highPriceKrw", equalTo(58900.0)))
                .andExpect(jsonPath("$.data[0].lowPriceKrw", equalTo(57500.0)))
                .andExpect(jsonPath("$.data[0].closePriceKrw", equalTo(58700.0)))
                .andExpect(jsonPath("$.data[0].tradingVolume", equalTo(19123456)))
                .andExpect(jsonPath("$.data[0].tradingValueKrw", equalTo(1.122334455E12)))
                .andExpect(jsonPath("$.data[0].source", equalTo("KRX_OPEN_API_DAILY_TRADE")));
    }

    @Test
    void foreignOwnershipRefreshApiStoresKisSnapshotInCache() throws Exception {
        when(foreignOwnershipRefreshService.refresh("005930", LocalDate.of(2025, 6, 4)))
                .thenReturn(new ForeignOwnershipRefreshResult(
                        "005930",
                        LocalDate.of(2025, 6, 4),
                        Optional.of(new ForeignOwnershipSnapshot(
                                "005930",
                                3_642_091_300L,
                                new BigDecimal("54.19"),
                                6_720_000_000L,
                                new BigDecimal("54.21"),
                                LocalDate.of(2025, 6, 4))),
                        "KIS_CURRENT_PRICE_FOREIGN_OWNERSHIP"));

        mockMvc.perform(post("/api/v1/market/stocks/005930/foreign-ownership/refresh")
                        .header("X-HANA-OMNILENS-API-KEY", "test-api-key")
                        .param("baseDate", "2025-06-04"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", equalTo(true)))
                .andExpect(jsonPath("$.data.stockCode", equalTo("005930")))
                .andExpect(jsonPath("$.data.baseDate", equalTo("2025-06-04")))
                .andExpect(jsonPath("$.data.refreshed", equalTo(true)))
                .andExpect(jsonPath("$.data.foreignOwnedQuantity", equalTo(3_642_091_300L)))
                .andExpect(jsonPath("$.data.foreignOwnershipRate", equalTo(54.19)))
                .andExpect(jsonPath("$.data.foreignLimitQuantity", equalTo(6_720_000_000L)))
                .andExpect(jsonPath("$.data.foreignLimitExhaustionRate", equalTo(54.21)))
                .andExpect(jsonPath("$.data.source", equalTo("KIS_CURRENT_PRICE_FOREIGN_OWNERSHIP")));
    }

    @Test
    void exchangeRateApiRejectsInvalidCurrencyAndRate() throws Exception {
        mockMvc.perform(put("/api/v1/market/exchange-rates/jpy")
                        .header("X-HANA-OMNILENS-API-KEY", "test-api-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fxRate\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", equalTo(false)))
                .andExpect(jsonPath("$.code", equalTo("COMMON_002")));
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
                .andExpect(jsonPath("$.data[0].stockCode", equalTo("086790")))
                .andExpect(jsonPath("$.data[0].stockName", equalTo("하나금융지주")))
                .andExpect(jsonPath("$.data[0].market", equalTo("KOSPI")));
    }
}

package com.hana.omnilens.market.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.hana.omnilens.market.domain.MarketDailyPrice;
import com.hana.omnilens.market.domain.StockSummary;
import com.hana.omnilens.provider.market.KrxOpenApiDailyTrade;
import com.hana.omnilens.provider.market.KrxOpenApiDailyTradeClient;

class MarketHistoryServiceTest {

    private final KrxOpenApiDailyTradeClient krxClient = mock(KrxOpenApiDailyTradeClient.class);
    private final MarketDailyPriceRepository dailyPriceRepository = mock(MarketDailyPriceRepository.class);
    private final StockMasterRepository stockMasterRepository = mock(StockMasterRepository.class);
    private final MarketHistoryService service = new MarketHistoryService(
            krxClient,
            dailyPriceRepository,
            stockMasterRepository,
            Clock.fixed(Instant.parse("2025-06-05T00:00:00Z"), ZoneId.of("Asia/Seoul")));

    @Test
    void collectDailyHistorySavesKrxRowsForSupportedStocks() {
        LocalDate baseDate = LocalDate.of(2025, 6, 4);
        when(krxClient.findAllDailyTrades(baseDate)).thenReturn(List.of(
                new KrxOpenApiDailyTrade(
                        baseDate,
                        "KR7005930003",
                        "005930",
                        "삼성전자",
                        "KOSPI",
                        new BigDecimal("57900"),
                        new BigDecimal("58900"),
                        new BigDecimal("57500"),
                        new BigDecimal("58700"),
                        new BigDecimal("1.91"),
                        19_123_456L,
                        new BigDecimal("1122334455000")),
                new KrxOpenApiDailyTrade(
                        baseDate,
                        "KR7999999999",
                        "999999",
                        "미지원종목",
                        "KONEX",
                        BigDecimal.ONE,
                        BigDecimal.ONE,
                        BigDecimal.ONE,
                        BigDecimal.ONE,
                        BigDecimal.ZERO,
                        1L,
                        BigDecimal.ONE)));
        when(stockMasterRepository.findByCode("005930")).thenReturn(Optional.of(new StockSummary(
                "005930",
                "삼성전자",
                "Samsung Electronics",
                "KOSPI",
                "KR7005930003",
                "00126380")));
        when(stockMasterRepository.findByCode("999999")).thenReturn(Optional.empty());
        when(dailyPriceRepository.upsertAll(anyList())).thenReturn(1);

        MarketHistoryCollectionResult result = service.collectDailyHistory(baseDate);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MarketDailyPrice>> captor = ArgumentCaptor.forClass(List.class);
        verify(dailyPriceRepository).upsertAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        MarketDailyPrice saved = captor.getValue().get(0);
        assertThat(saved.stockCode()).isEqualTo("005930");
        assertThat(saved.openPriceKrw()).isEqualByComparingTo("57900");
        assertThat(saved.highPriceKrw()).isEqualByComparingTo("58900");
        assertThat(saved.lowPriceKrw()).isEqualByComparingTo("57500");
        assertThat(saved.closePriceKrw()).isEqualByComparingTo("58700");
        assertThat(saved.tradingValueKrw()).isEqualByComparingTo("1122334455000");
        assertThat(saved.source()).isEqualTo("KRX_OPEN_API_DAILY_TRADE");
        assertThat(result.requestedCount()).isEqualTo(2);
        assertThat(result.savedCount()).isEqualTo(1);
    }
}

package com.hana.omnilens.market.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.hana.omnilens.market.domain.MarketQuote;
import com.hana.omnilens.market.domain.StockSummary;
import com.hana.omnilens.provider.market.PublicDataStockPriceSnapshot;
import com.hana.omnilens.provider.market.PublicDataStockSecuritiesClient;

class MarketDataServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2025-06-04T00:00:00Z"),
            ZoneId.of("Asia/Seoul"));

    @Test
    void getQuoteUsesPublicDataSnapshotWhenAvailable() {
        PublicDataStockSecuritiesClient client = mock(PublicDataStockSecuritiesClient.class);
        StockMasterRepository repository = mock(StockMasterRepository.class);
        MarketDataService service = new MarketDataService(client, repository, FIXED_CLOCK);

        when(repository.findByCode("005930")).thenReturn(Optional.of(
                new StockSummary("005930", "삼성전자", "Samsung Electronics", "KOSPI", "00126380")));
        when(client.findPrice("005930", LocalDate.of(2025, 6, 3))).thenReturn(Optional.of(
                new PublicDataStockPriceSnapshot(
                        "005930",
                        "삼성전자",
                        "KOSPI",
                        new BigDecimal("80100"),
                        new BigDecimal("2.11"),
                        9800000L,
                        LocalDate.of(2025, 6, 3))));

        MarketQuote quote = service.getQuote("005930", "USD", new BigDecimal("0.00072"));

        assertThat(quote.currentPriceKrw()).isEqualByComparingTo("80100");
        assertThat(quote.localCurrencyPrice()).isEqualByComparingTo("57.6720");
        assertThat(quote.changeRate()).isEqualByComparingTo("2.11");
        assertThat(quote.volume()).isEqualTo(9_800_000L);
        assertThat(quote.source()).isEqualTo("PUBLIC_DATA_STOCK_SECURITIES");
    }

    @Test
    void getQuoteFallsBackWhenProviderIsUnavailable() {
        PublicDataStockSecuritiesClient client = mock(PublicDataStockSecuritiesClient.class);
        StockMasterRepository repository = mock(StockMasterRepository.class);
        MarketDataService service = new MarketDataService(client, repository, FIXED_CLOCK);

        when(repository.findByCode("005930")).thenReturn(Optional.of(
                new StockSummary("005930", "삼성전자", "Samsung Electronics", "KOSPI", "00126380")));
        when(client.findPrice("005930", LocalDate.of(2025, 6, 3)))
                .thenThrow(new IllegalStateException("provider is not configured"));

        MarketQuote quote = service.getQuote("005930", "USD", new BigDecimal("0.00072"));

        assertThat(quote.currentPriceKrw()).isEqualByComparingTo("78500");
        assertThat(quote.localCurrencyPrice()).isEqualByComparingTo("56.5200");
        assertThat(quote.source()).isEqualTo("MOCK_MARKET_DATA");
    }
}

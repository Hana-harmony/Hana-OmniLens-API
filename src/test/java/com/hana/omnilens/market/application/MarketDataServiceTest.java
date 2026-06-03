package com.hana.omnilens.market.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
import com.hana.omnilens.provider.market.KisCurrentPriceClient;
import com.hana.omnilens.provider.market.KisCurrentPriceSnapshot;
import com.hana.omnilens.provider.market.KrxForeignOwnershipClient;
import com.hana.omnilens.provider.market.KrxForeignOwnershipSnapshot;
import com.hana.omnilens.provider.market.PublicDataStockPriceSnapshot;
import com.hana.omnilens.provider.market.PublicDataStockSecuritiesClient;

class MarketDataServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2025-06-04T00:00:00Z"),
            ZoneId.of("Asia/Seoul"));

    @Test
    void getQuoteUsesKisCurrentPriceBeforePublicDataSnapshot() {
        PublicDataStockSecuritiesClient client = mock(PublicDataStockSecuritiesClient.class);
        KisCurrentPriceClient kisCurrentPriceClient = mock(KisCurrentPriceClient.class);
        KrxForeignOwnershipClient foreignOwnershipClient = mock(KrxForeignOwnershipClient.class);
        StockMasterRepository repository = mock(StockMasterRepository.class);
        ForeignOwnershipSnapshotCache cache = new InMemoryForeignOwnershipSnapshotCache();
        MarketDataService service = new MarketDataService(
                client,
                kisCurrentPriceClient,
                foreignOwnershipClient,
                repository,
                cache,
                FIXED_CLOCK);

        when(repository.findByCode("005930")).thenReturn(Optional.of(samsungElectronics()));
        when(kisCurrentPriceClient.findCurrentPrice("005930")).thenReturn(Optional.of(
                new KisCurrentPriceSnapshot(
                        "005930",
                        "삼성전자",
                        new BigDecimal("81200"),
                        new BigDecimal("1.87"),
                        15_500_000L)));
        when(foreignOwnershipClient.findForeignOwnership(
                "005930",
                "삼성전자",
                "KR7005930003",
                LocalDate.of(2025, 6, 3))).thenReturn(Optional.of(new KrxForeignOwnershipSnapshot(
                        "005930",
                        3_642_091_300L,
                        new BigDecimal("54.19"),
                        6_720_000_000L,
                        new BigDecimal("54.21"),
                        LocalDate.of(2025, 6, 3))));

        MarketQuote quote = service.getQuote("005930", "USD", new BigDecimal("0.00072"));

        assertThat(quote.currentPriceKrw()).isEqualByComparingTo("81200");
        assertThat(quote.localCurrencyPrice()).isEqualByComparingTo("58.4640");
        assertThat(quote.changeRate()).isEqualByComparingTo("1.87");
        assertThat(quote.volume()).isEqualTo(15_500_000L);
        assertThat(quote.market()).isEqualTo("KOSPI");
        assertThat(quote.source()).isEqualTo("KIS_OPEN_API+KRX_FOREIGN_OWNERSHIP");
    }

    @Test
    void getQuoteUsesPublicDataSnapshotWhenAvailable() {
        PublicDataStockSecuritiesClient client = mock(PublicDataStockSecuritiesClient.class);
        KisCurrentPriceClient kisCurrentPriceClient = mock(KisCurrentPriceClient.class);
        KrxForeignOwnershipClient foreignOwnershipClient = mock(KrxForeignOwnershipClient.class);
        StockMasterRepository repository = mock(StockMasterRepository.class);
        ForeignOwnershipSnapshotCache cache = new InMemoryForeignOwnershipSnapshotCache();
        MarketDataService service = new MarketDataService(
                client,
                kisCurrentPriceClient,
                foreignOwnershipClient,
                repository,
                cache,
                FIXED_CLOCK);

        when(repository.findByCode("005930")).thenReturn(Optional.of(
                new StockSummary(
                        "005930",
                        "삼성전자",
                        "Samsung Electronics",
                        "KOSPI",
                        "KR7005930003",
                        "00126380")));
        when(kisCurrentPriceClient.findCurrentPrice("005930")).thenThrow(
                new IllegalStateException("kis is not configured"));
        when(client.findPrice("005930", LocalDate.of(2025, 6, 3))).thenReturn(Optional.of(
                new PublicDataStockPriceSnapshot(
                        "005930",
                        "삼성전자",
                        "KOSPI",
                        new BigDecimal("80100"),
                        new BigDecimal("2.11"),
                        9800000L,
                        LocalDate.of(2025, 6, 3))));
        when(foreignOwnershipClient.findForeignOwnership(
                "005930",
                "삼성전자",
                "KR7005930003",
                LocalDate.of(2025, 6, 3))).thenReturn(Optional.of(new KrxForeignOwnershipSnapshot(
                        "005930",
                        3_642_091_300L,
                        new BigDecimal("54.19"),
                        6_720_000_000L,
                        new BigDecimal("54.21"),
                        LocalDate.of(2025, 6, 3))));

        MarketQuote quote = service.getQuote("005930", "USD", new BigDecimal("0.00072"));

        assertThat(quote.currentPriceKrw()).isEqualByComparingTo("80100");
        assertThat(quote.localCurrencyPrice()).isEqualByComparingTo("57.6720");
        assertThat(quote.changeRate()).isEqualByComparingTo("2.11");
        assertThat(quote.volume()).isEqualTo(9_800_000L);
        assertThat(quote.foreignOwnedQuantity()).isEqualTo(3_642_091_300L);
        assertThat(quote.foreignOwnershipRate()).isEqualByComparingTo("54.19");
        assertThat(quote.foreignLimitExhaustionRate()).isEqualByComparingTo("54.21");
        assertThat(quote.source()).isEqualTo("PUBLIC_DATA_STOCK_SECURITIES+KRX_FOREIGN_OWNERSHIP");
    }

    @Test
    void getQuoteFallsBackWhenProviderIsUnavailable() {
        PublicDataStockSecuritiesClient client = mock(PublicDataStockSecuritiesClient.class);
        KisCurrentPriceClient kisCurrentPriceClient = mock(KisCurrentPriceClient.class);
        KrxForeignOwnershipClient foreignOwnershipClient = mock(KrxForeignOwnershipClient.class);
        StockMasterRepository repository = mock(StockMasterRepository.class);
        ForeignOwnershipSnapshotCache cache = new InMemoryForeignOwnershipSnapshotCache();
        MarketDataService service = new MarketDataService(
                client,
                kisCurrentPriceClient,
                foreignOwnershipClient,
                repository,
                cache,
                FIXED_CLOCK);

        when(repository.findByCode("005930")).thenReturn(Optional.of(
                new StockSummary(
                        "005930",
                        "삼성전자",
                        "Samsung Electronics",
                        "KOSPI",
                        "KR7005930003",
                        "00126380")));
        when(kisCurrentPriceClient.findCurrentPrice("005930"))
                .thenThrow(new IllegalStateException("kis is not configured"));
        when(client.findPrice("005930", LocalDate.of(2025, 6, 3)))
                .thenThrow(new IllegalStateException("provider is not configured"));
        when(foreignOwnershipClient.findForeignOwnership(
                "005930",
                "삼성전자",
                "KR7005930003",
                LocalDate.of(2025, 6, 3))).thenThrow(new IllegalStateException("krx is unavailable"));

        MarketQuote quote = service.getQuote("005930", "USD", new BigDecimal("0.00072"));

        assertThat(quote.currentPriceKrw()).isEqualByComparingTo("78500");
        assertThat(quote.localCurrencyPrice()).isEqualByComparingTo("56.5200");
        assertThat(quote.source()).isEqualTo("MOCK_MARKET_DATA");
    }

    @Test
    void getQuoteRetriesKrxForeignOwnershipAfterDateFailure() {
        PublicDataStockSecuritiesClient client = mock(PublicDataStockSecuritiesClient.class);
        KisCurrentPriceClient kisCurrentPriceClient = mock(KisCurrentPriceClient.class);
        KrxForeignOwnershipClient foreignOwnershipClient = mock(KrxForeignOwnershipClient.class);
        StockMasterRepository repository = mock(StockMasterRepository.class);
        ForeignOwnershipSnapshotCache cache = new InMemoryForeignOwnershipSnapshotCache();
        MarketDataService service = new MarketDataService(
                client,
                kisCurrentPriceClient,
                foreignOwnershipClient,
                repository,
                cache,
                FIXED_CLOCK);
        StockSummary stock = samsungElectronics();

        when(repository.findByCode("005930")).thenReturn(Optional.of(stock));
        when(kisCurrentPriceClient.findCurrentPrice("005930")).thenReturn(Optional.empty());
        when(client.findPrice("005930", LocalDate.of(2025, 6, 3))).thenReturn(Optional.empty());
        when(foreignOwnershipClient.findForeignOwnership(
                "005930",
                "삼성전자",
                "KR7005930003",
                LocalDate.of(2025, 6, 3))).thenThrow(new IllegalStateException("krx date is unavailable"));
        when(foreignOwnershipClient.findForeignOwnership(
                "005930",
                "삼성전자",
                "KR7005930003",
                LocalDate.of(2025, 6, 2))).thenReturn(Optional.of(new KrxForeignOwnershipSnapshot(
                        "005930",
                        3_500_000_000L,
                        new BigDecimal("52.10"),
                        6_720_000_000L,
                        new BigDecimal("52.08"),
                        LocalDate.of(2025, 6, 2))));

        MarketQuote quote = service.getQuote("005930", "USD", new BigDecimal("0.00072"));

        assertThat(quote.foreignOwnedQuantity()).isEqualTo(3_500_000_000L);
        assertThat(quote.foreignOwnershipRate()).isEqualByComparingTo("52.10");
        assertThat(quote.foreignOwnershipBaseDate()).isEqualTo(LocalDate.of(2025, 6, 2));
        assertThat(quote.source()).isEqualTo("MOCK_MARKET_DATA+KRX_FOREIGN_OWNERSHIP");
        assertThat(cache.find("005930")).isPresent();
        verify(foreignOwnershipClient).findForeignOwnership(
                "005930",
                "삼성전자",
                "KR7005930003",
                LocalDate.of(2025, 6, 3));
        verify(foreignOwnershipClient).findForeignOwnership(
                "005930",
                "삼성전자",
                "KR7005930003",
                LocalDate.of(2025, 6, 2));
    }

    @Test
    void getQuoteUsesCachedForeignOwnershipWhenKrxIsUnavailable() {
        PublicDataStockSecuritiesClient client = mock(PublicDataStockSecuritiesClient.class);
        KisCurrentPriceClient kisCurrentPriceClient = mock(KisCurrentPriceClient.class);
        KrxForeignOwnershipClient foreignOwnershipClient = mock(KrxForeignOwnershipClient.class);
        StockMasterRepository repository = mock(StockMasterRepository.class);
        ForeignOwnershipSnapshotCache cache = new InMemoryForeignOwnershipSnapshotCache();
        MarketDataService service = new MarketDataService(
                client,
                kisCurrentPriceClient,
                foreignOwnershipClient,
                repository,
                cache,
                FIXED_CLOCK);
        StockSummary stock = samsungElectronics();
        cache.put(new KrxForeignOwnershipSnapshot(
                "005930",
                3_400_000_000L,
                new BigDecimal("51.01"),
                6_720_000_000L,
                new BigDecimal("50.99"),
                LocalDate.of(2025, 6, 2)));

        when(repository.findByCode("005930")).thenReturn(Optional.of(stock));
        when(kisCurrentPriceClient.findCurrentPrice("005930")).thenReturn(Optional.empty());
        when(client.findPrice("005930", LocalDate.of(2025, 6, 3))).thenReturn(Optional.empty());
        when(foreignOwnershipClient.findForeignOwnership(
                eq("005930"),
                eq("삼성전자"),
                eq("KR7005930003"),
                any(LocalDate.class))).thenThrow(new IllegalStateException("krx is unavailable"));

        MarketQuote quote = service.getQuote("005930", "USD", new BigDecimal("0.00072"));

        assertThat(quote.foreignOwnedQuantity()).isEqualTo(3_400_000_000L);
        assertThat(quote.foreignOwnershipRate()).isEqualByComparingTo("51.01");
        assertThat(quote.foreignLimitExhaustionRate()).isEqualByComparingTo("50.99");
        assertThat(quote.foreignOwnershipBaseDate()).isEqualTo(LocalDate.of(2025, 6, 2));
        assertThat(quote.source()).isEqualTo("MOCK_MARKET_DATA+KRX_FOREIGN_OWNERSHIP_CACHE");
    }

    private StockSummary samsungElectronics() {
        return new StockSummary(
                "005930",
                "삼성전자",
                "Samsung Electronics",
                "KOSPI",
                "KR7005930003",
                "00126380");
    }
}

package com.hana.omnilens.market.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.hana.omnilens.market.domain.MarketQuote;
import com.hana.omnilens.market.domain.Orderability;
import com.hana.omnilens.market.domain.OrderBook;
import com.hana.omnilens.market.domain.StockSummary;
import com.hana.omnilens.provider.market.KisCurrentPriceClient;
import com.hana.omnilens.provider.market.KisCurrentPriceSnapshot;
import com.hana.omnilens.provider.market.KisRealtimeOrderBookSnapshot;
import com.hana.omnilens.provider.market.KisRealtimeTradeTick;
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
        StockMasterRepository repository = mock(StockMasterRepository.class);
        ForeignOwnershipSnapshotCache cache = new InMemoryForeignOwnershipSnapshotCache();
        MarketDataService service = new MarketDataService(
                client,
                kisCurrentPriceClient,
                repository,
                cache,
                new InMemoryExchangeRateCache(),
                new InMemoryRealtimeMarketDataCache(),
                FIXED_CLOCK);

        when(repository.findByCode("005930")).thenReturn(Optional.of(samsungElectronics()));
        when(kisCurrentPriceClient.findCurrentPrice("005930")).thenReturn(Optional.of(
                new KisCurrentPriceSnapshot(
                        "005930",
                        "삼성전자",
                        new BigDecimal("81200"),
                        new BigDecimal("1.87"),
                        15_500_000L)));

        MarketQuote quote = service.getQuote("005930", "USD", new BigDecimal("0.00072"));

        assertThat(quote.currentPriceKrw()).isEqualByComparingTo("81200");
        assertThat(quote.localCurrencyPrice()).isEqualByComparingTo("58.4640");
        assertThat(quote.changeRate()).isEqualByComparingTo("1.87");
        assertThat(quote.volume()).isEqualTo(15_500_000L);
        assertThat(quote.market()).isEqualTo("KOSPI");
        assertThat(quote.source()).isEqualTo("KIS_OPEN_API");
    }

    @Test
    void getQuoteUsesPublicDataSnapshotWhenAvailable() {
        PublicDataStockSecuritiesClient client = mock(PublicDataStockSecuritiesClient.class);
        KisCurrentPriceClient kisCurrentPriceClient = mock(KisCurrentPriceClient.class);
        StockMasterRepository repository = mock(StockMasterRepository.class);
        ForeignOwnershipSnapshotCache cache = new InMemoryForeignOwnershipSnapshotCache();
        MarketDataService service = new MarketDataService(
                client,
                kisCurrentPriceClient,
                repository,
                cache,
                new InMemoryExchangeRateCache(),
                new InMemoryRealtimeMarketDataCache(),
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

        MarketQuote quote = service.getQuote("005930", "USD", new BigDecimal("0.00072"));

        assertThat(quote.currentPriceKrw()).isEqualByComparingTo("80100");
        assertThat(quote.localCurrencyPrice()).isEqualByComparingTo("57.6720");
        assertThat(quote.changeRate()).isEqualByComparingTo("2.11");
        assertThat(quote.volume()).isEqualTo(9_800_000L);
        assertThat(quote.foreignOwnedQuantity()).isEqualTo(3_642_091_300L);
        assertThat(quote.foreignOwnershipRate()).isEqualByComparingTo("54.19");
        assertThat(quote.foreignLimitExhaustionRate()).isEqualByComparingTo("54.19");
        assertThat(quote.source()).isEqualTo("PUBLIC_DATA_STOCK_SECURITIES");
    }

    @Test
    void getQuoteFallsBackWhenProviderIsUnavailable() {
        PublicDataStockSecuritiesClient client = mock(PublicDataStockSecuritiesClient.class);
        KisCurrentPriceClient kisCurrentPriceClient = mock(KisCurrentPriceClient.class);
        StockMasterRepository repository = mock(StockMasterRepository.class);
        ForeignOwnershipSnapshotCache cache = new InMemoryForeignOwnershipSnapshotCache();
        MarketDataService service = new MarketDataService(
                client,
                kisCurrentPriceClient,
                repository,
                cache,
                new InMemoryExchangeRateCache(),
                new InMemoryRealtimeMarketDataCache(),
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

        MarketQuote quote = service.getQuote("005930", "USD", new BigDecimal("0.00072"));

        assertThat(quote.currentPriceKrw()).isEqualByComparingTo("78500");
        assertThat(quote.localCurrencyPrice()).isEqualByComparingTo("56.5200");
        assertThat(quote.source()).isEqualTo("MOCK_MARKET_DATA");
    }

    @Test
    void getQuoteUsesCachedForeignOwnershipWhenPresent() {
        PublicDataStockSecuritiesClient client = mock(PublicDataStockSecuritiesClient.class);
        KisCurrentPriceClient kisCurrentPriceClient = mock(KisCurrentPriceClient.class);
        StockMasterRepository repository = mock(StockMasterRepository.class);
        ForeignOwnershipSnapshotCache cache = new InMemoryForeignOwnershipSnapshotCache();
        MarketDataService service = new MarketDataService(
                client,
                kisCurrentPriceClient,
                repository,
                cache,
                new InMemoryExchangeRateCache(),
                new InMemoryRealtimeMarketDataCache(),
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

        MarketQuote quote = service.getQuote("005930", "USD", new BigDecimal("0.00072"));

        assertThat(quote.foreignOwnedQuantity()).isEqualTo(3_400_000_000L);
        assertThat(quote.foreignOwnershipRate()).isEqualByComparingTo("51.01");
        assertThat(quote.foreignLimitExhaustionRate()).isEqualByComparingTo("50.99");
        assertThat(quote.foreignOwnershipBaseDate()).isEqualTo(LocalDate.of(2025, 6, 2));
        assertThat(quote.source()).isEqualTo("MOCK_MARKET_DATA+KRX_FOREIGN_OWNERSHIP_CACHE");
    }

    @Test
    void getQuoteUsesCachedExchangeRateWhenRequestRateIsMissing() {
        PublicDataStockSecuritiesClient client = mock(PublicDataStockSecuritiesClient.class);
        KisCurrentPriceClient kisCurrentPriceClient = mock(KisCurrentPriceClient.class);
        StockMasterRepository repository = mock(StockMasterRepository.class);
        ExchangeRateCache exchangeRateCache = new InMemoryExchangeRateCache();
        MarketDataService service = new MarketDataService(
                client,
                kisCurrentPriceClient,
                repository,
                new InMemoryForeignOwnershipSnapshotCache(),
                exchangeRateCache,
                new InMemoryRealtimeMarketDataCache(),
                FIXED_CLOCK);

        when(repository.findByCode("005930")).thenReturn(Optional.of(samsungElectronics()));
        when(kisCurrentPriceClient.findCurrentPrice("005930"))
                .thenThrow(new IllegalStateException("kis is not configured"));
        when(client.findPrice("005930", LocalDate.of(2025, 6, 3)))
                .thenThrow(new IllegalStateException("provider is not configured"));
        service.updateExchangeRate("USD", new BigDecimal("0.00072"));

        MarketQuote quote = service.getQuote("005930", "USD", null);

        assertThat(quote.localCurrencyPrice()).isEqualByComparingTo("56.5200");
        assertThat(exchangeRateCache.find("USD")).isPresent();
    }

    @Test
    void getQuoteUsesRequestExchangeRateBeforeCachedRate() {
        PublicDataStockSecuritiesClient client = mock(PublicDataStockSecuritiesClient.class);
        KisCurrentPriceClient kisCurrentPriceClient = mock(KisCurrentPriceClient.class);
        StockMasterRepository repository = mock(StockMasterRepository.class);
        ExchangeRateCache exchangeRateCache = new InMemoryExchangeRateCache();
        MarketDataService service = new MarketDataService(
                client,
                kisCurrentPriceClient,
                repository,
                new InMemoryForeignOwnershipSnapshotCache(),
                exchangeRateCache,
                new InMemoryRealtimeMarketDataCache(),
                FIXED_CLOCK);

        when(repository.findByCode("005930")).thenReturn(Optional.of(samsungElectronics()));
        when(kisCurrentPriceClient.findCurrentPrice("005930"))
                .thenThrow(new IllegalStateException("kis is not configured"));
        when(client.findPrice("005930", LocalDate.of(2025, 6, 3)))
                .thenThrow(new IllegalStateException("provider is not configured"));
        service.updateExchangeRate("USD", new BigDecimal("0.00072"));

        MarketQuote quote = service.getQuote("005930", "USD", new BigDecimal("0.00080"));

        assertThat(quote.localCurrencyPrice()).isEqualByComparingTo("62.8000");
    }

    @Test
    void getQuoteIncludesFxMetadataForCachedExchangeRate() {
        PublicDataStockSecuritiesClient client = mock(PublicDataStockSecuritiesClient.class);
        KisCurrentPriceClient kisCurrentPriceClient = mock(KisCurrentPriceClient.class);
        StockMasterRepository repository = mock(StockMasterRepository.class);
        ExchangeRateCache exchangeRateCache = new InMemoryExchangeRateCache();
        MarketDataService service = new MarketDataService(
                client,
                kisCurrentPriceClient,
                repository,
                new InMemoryForeignOwnershipSnapshotCache(),
                exchangeRateCache,
                new InMemoryRealtimeMarketDataCache(),
                FIXED_CLOCK);

        when(repository.findByCode("005930")).thenReturn(Optional.of(samsungElectronics()));
        when(kisCurrentPriceClient.findCurrentPrice("005930")).thenReturn(Optional.empty());
        when(client.findPrice("005930", LocalDate.of(2025, 6, 3))).thenReturn(Optional.empty());
        service.updateExchangeRate("USD", new BigDecimal("0.00072"));

        MarketQuote quote = service.getQuote("005930", "USD", null);

        assertThat(quote.fxRate()).isEqualByComparingTo("0.00072");
        assertThat(quote.fxRateTime()).isEqualTo(Instant.parse("2025-06-04T00:00:00Z"));
        assertThat(quote.fxRateSource()).isEqualTo("EXCHANGE_RATE_CACHE");
        assertThat(quote.fxStale()).isFalse();
    }

    @Test
    void getQuotesReturnsAllSeededStocksWhenStockCodesAreMissing() {
        PublicDataStockSecuritiesClient client = mock(PublicDataStockSecuritiesClient.class);
        KisCurrentPriceClient kisCurrentPriceClient = mock(KisCurrentPriceClient.class);
        StockMasterRepository repository = mock(StockMasterRepository.class);
        MarketDataService service = new MarketDataService(
                client,
                kisCurrentPriceClient,
                repository,
                new InMemoryForeignOwnershipSnapshotCache(),
                new InMemoryExchangeRateCache(),
                new InMemoryRealtimeMarketDataCache(),
                FIXED_CLOCK);
        StockSummary skHynix = new StockSummary(
                "000660",
                "SK하이닉스",
                "SK hynix",
                "KOSPI",
                "KR7000660001",
                "00164779");

        when(repository.findAll(10)).thenReturn(List.of(skHynix, samsungElectronics()));
        when(repository.findByCode("000660")).thenReturn(Optional.of(skHynix));
        when(repository.findByCode("005930")).thenReturn(Optional.of(samsungElectronics()));
        when(kisCurrentPriceClient.findCurrentPrice("000660")).thenReturn(Optional.empty());
        when(kisCurrentPriceClient.findCurrentPrice("005930")).thenReturn(Optional.empty());
        when(client.findPrice("000660", LocalDate.of(2025, 6, 3))).thenReturn(Optional.empty());
        when(client.findPrice("005930", LocalDate.of(2025, 6, 3))).thenReturn(Optional.empty());

        List<MarketQuote> quotes = service.getQuotes(List.of(), "KOSPI", "USD", new BigDecimal("0.00072"), 10);

        assertThat(quotes).extracting(MarketQuote::stockCode).containsExactly("000660", "005930");
        assertThat(quotes).extracting(MarketQuote::fxRateSource).containsOnly("PARTNER_REQUEST");
    }

    @Test
    void getQuotesDeduplicatesRequestedStockCodesAndPreservesOrder() {
        PublicDataStockSecuritiesClient client = mock(PublicDataStockSecuritiesClient.class);
        KisCurrentPriceClient kisCurrentPriceClient = mock(KisCurrentPriceClient.class);
        StockMasterRepository repository = mock(StockMasterRepository.class);
        MarketDataService service = new MarketDataService(
                client,
                kisCurrentPriceClient,
                repository,
                new InMemoryForeignOwnershipSnapshotCache(),
                new InMemoryExchangeRateCache(),
                new InMemoryRealtimeMarketDataCache(),
                FIXED_CLOCK);
        StockSummary skHynix = new StockSummary(
                "000660",
                "SK하이닉스",
                "SK hynix",
                "KOSPI",
                "KR7000660001",
                "00164779");

        when(repository.findByCode("000660")).thenReturn(Optional.of(skHynix));
        when(repository.findByCode("005930")).thenReturn(Optional.of(samsungElectronics()));
        when(kisCurrentPriceClient.findCurrentPrice("000660")).thenReturn(Optional.empty());
        when(kisCurrentPriceClient.findCurrentPrice("005930")).thenReturn(Optional.empty());
        when(client.findPrice("000660", LocalDate.of(2025, 6, 3))).thenReturn(Optional.empty());
        when(client.findPrice("005930", LocalDate.of(2025, 6, 3))).thenReturn(Optional.empty());

        List<MarketQuote> quotes = service.getQuotes(
                List.of("000660", "005930", "000660"),
                null,
                "USD",
                new BigDecimal("0.00072"),
                10);

        assertThat(quotes).extracting(MarketQuote::stockCode).containsExactly("000660", "005930");
    }

    @Test
    void getQuoteUsesRealtimeTradeCacheBeforeKisRest() {
        PublicDataStockSecuritiesClient client = mock(PublicDataStockSecuritiesClient.class);
        KisCurrentPriceClient kisCurrentPriceClient = mock(KisCurrentPriceClient.class);
        StockMasterRepository repository = mock(StockMasterRepository.class);
        RealtimeMarketDataCache realtimeCache = new InMemoryRealtimeMarketDataCache();
        MarketDataService service = new MarketDataService(
                client,
                kisCurrentPriceClient,
                repository,
                new InMemoryForeignOwnershipSnapshotCache(),
                new InMemoryExchangeRateCache(),
                realtimeCache,
                FIXED_CLOCK);

        when(repository.findByCode("005930")).thenReturn(Optional.of(samsungElectronics()));
        realtimeCache.putTrade(new KisRealtimeTradeTick(
                "005930",
                "093000",
                new BigDecimal("81500"),
                new BigDecimal("1.92"),
                new BigDecimal("81600"),
                new BigDecimal("81400"),
                1200L,
                16_200_000L,
                LocalDate.of(2025, 6, 4)));

        MarketQuote quote = service.getQuote("005930", "USD", new BigDecimal("0.00072"));

        assertThat(quote.currentPriceKrw()).isEqualByComparingTo("81500");
        assertThat(quote.changeRate()).isEqualByComparingTo("1.92");
        assertThat(quote.volume()).isEqualTo(16_200_000L);
        assertThat(quote.source()).isEqualTo("KIS_WEBSOCKET_TRADE");
    }

    @Test
    void getOrderBookUsesRealtimeOrderBookCache() {
        PublicDataStockSecuritiesClient client = mock(PublicDataStockSecuritiesClient.class);
        KisCurrentPriceClient kisCurrentPriceClient = mock(KisCurrentPriceClient.class);
        StockMasterRepository repository = mock(StockMasterRepository.class);
        RealtimeMarketDataCache realtimeCache = new InMemoryRealtimeMarketDataCache();
        MarketDataService service = new MarketDataService(
                client,
                kisCurrentPriceClient,
                repository,
                new InMemoryForeignOwnershipSnapshotCache(),
                new InMemoryExchangeRateCache(),
                realtimeCache,
                FIXED_CLOCK);
        realtimeCache.putOrderBook(new KisRealtimeOrderBookSnapshot(
                "005930",
                "093000",
                List.of(new KisRealtimeOrderBookSnapshot.Level(new BigDecimal("81600"), 1200L)),
                List.of(new KisRealtimeOrderBookSnapshot.Level(new BigDecimal("81400"), 1800L)),
                16_200_000L));

        OrderBook orderBook = service.getOrderBook("005930");

        assertThat(orderBook.asks()).hasSize(1);
        assertThat(orderBook.asks().get(0).priceKrw()).isEqualByComparingTo("81600");
        assertThat(orderBook.bids().get(0).quantity()).isEqualTo(1800L);
        assertThat(orderBook.source()).isEqualTo("KIS_WEBSOCKET_ORDERBOOK");
    }

    @Test
    void getOrderabilityBlocksBuyWhenPredictedForeignLimitIsExceeded() {
        PublicDataStockSecuritiesClient client = mock(PublicDataStockSecuritiesClient.class);
        KisCurrentPriceClient kisCurrentPriceClient = mock(KisCurrentPriceClient.class);
        StockMasterRepository repository = mock(StockMasterRepository.class);
        ForeignOwnershipSnapshotCache cache = new InMemoryForeignOwnershipSnapshotCache();
        MarketDataService service = new MarketDataService(
                client,
                kisCurrentPriceClient,
                repository,
                cache,
                new InMemoryExchangeRateCache(),
                new InMemoryRealtimeMarketDataCache(),
                FIXED_CLOCK);
        cache.put(new KrxForeignOwnershipSnapshot(
                "005930",
                990L,
                new BigDecimal("49.50"),
                1_000L,
                new BigDecimal("99.0000"),
                LocalDate.of(2025, 6, 3)));

        when(repository.findByCode("005930")).thenReturn(Optional.of(samsungElectronics()));
        when(kisCurrentPriceClient.findCurrentPrice("005930")).thenReturn(Optional.empty());
        when(client.findPrice("005930", LocalDate.of(2025, 6, 3))).thenReturn(Optional.empty());

        Orderability orderability = service.getOrderability("005930", "BUY", 20);

        assertThat(orderability.orderable()).isFalse();
        assertThat(orderability.orderBlockedReason()).isEqualTo("FOREIGN_LIMIT_EXCEEDED");
        assertThat(orderability.foreignLimitExceeded()).isTrue();
        assertThat(orderability.currentForeignLimitExhaustionRate()).isEqualByComparingTo("99.0000");
        assertThat(orderability.predictedForeignLimitExhaustionRate()).isEqualByComparingTo("101.000000");
        assertThat(orderability.foreignOwnershipBaseDate()).isEqualTo(LocalDate.of(2025, 6, 3));
        assertThat(orderability.priceLimitState()).isEqualTo("NORMAL");
        assertThat(orderability.viActive()).isFalse();
        assertThat(orderability.source()).isEqualTo("ORDERABILITY_MOCK_MARKET_DATA+KRX_FOREIGN_OWNERSHIP_CACHE");
    }

    @Test
    void getOrderabilityAllowsSellEvenWhenForeignLimitIsExhausted() {
        PublicDataStockSecuritiesClient client = mock(PublicDataStockSecuritiesClient.class);
        KisCurrentPriceClient kisCurrentPriceClient = mock(KisCurrentPriceClient.class);
        StockMasterRepository repository = mock(StockMasterRepository.class);
        ForeignOwnershipSnapshotCache cache = new InMemoryForeignOwnershipSnapshotCache();
        MarketDataService service = new MarketDataService(
                client,
                kisCurrentPriceClient,
                repository,
                cache,
                new InMemoryExchangeRateCache(),
                new InMemoryRealtimeMarketDataCache(),
                FIXED_CLOCK);
        cache.put(new KrxForeignOwnershipSnapshot(
                "005930",
                1_000L,
                new BigDecimal("50.00"),
                1_000L,
                new BigDecimal("100.0000"),
                LocalDate.of(2025, 6, 3)));

        when(repository.findByCode("005930")).thenReturn(Optional.of(samsungElectronics()));
        when(kisCurrentPriceClient.findCurrentPrice("005930")).thenReturn(Optional.empty());
        when(client.findPrice("005930", LocalDate.of(2025, 6, 3))).thenReturn(Optional.empty());

        Orderability orderability = service.getOrderability("005930", "SELL", 10);

        assertThat(orderability.orderable()).isTrue();
        assertThat(orderability.orderBlockedReason()).isNull();
        assertThat(orderability.foreignLimitExceeded()).isFalse();
        assertThat(orderability.predictedForeignLimitExhaustionRate()).isEqualByComparingTo("100.0000");
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

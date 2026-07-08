package com.hana.omnilens.market.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClientException;

import com.hana.omnilens.market.domain.MarketDailyPrice;
import com.hana.omnilens.market.domain.MarketQuote;
import com.hana.omnilens.market.domain.MarketIndexIntradayPrice;
import com.hana.omnilens.market.domain.MarketIndexQuote;
import com.hana.omnilens.market.domain.MarketIntradayPrice;
import com.hana.omnilens.market.domain.Orderability;
import com.hana.omnilens.market.domain.OrderBook;
import com.hana.omnilens.market.domain.ForeignOwnershipDailySnapshot;
import com.hana.omnilens.market.domain.ForeignOwnershipPrediction;
import com.hana.omnilens.market.domain.StockDetail;
import com.hana.omnilens.market.domain.StockSummary;
import com.hana.omnilens.provider.ai.HannahAiForeignOwnershipPredictionClient;
import com.hana.omnilens.provider.ai.HannahAiForeignOwnershipPredictionResponse;
import com.hana.omnilens.provider.market.KisCurrentPriceClient;
import com.hana.omnilens.provider.market.KisCurrentPriceSnapshot;
import com.hana.omnilens.provider.market.KisIndexCurrentPriceClient;
import com.hana.omnilens.provider.market.KisIndexCurrentPriceSnapshot;
import com.hana.omnilens.provider.market.KisRealtimeOrderBookSnapshot;
import com.hana.omnilens.provider.market.KisRealtimeIndexTick;
import com.hana.omnilens.provider.market.KisRealtimeTradeTick;
import com.hana.omnilens.provider.market.KisRestOrderBookClient;
import com.hana.omnilens.provider.market.KisRestOrderBookSnapshot;
import com.hana.omnilens.provider.market.ForeignOwnershipSnapshot;
import com.hana.omnilens.provider.market.PublicDataStockPriceSnapshot;
import com.hana.omnilens.provider.market.PublicDataStockSecuritiesClient;

class MarketDataServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2025-06-04T00:00:00Z"),
            ZoneId.of("Asia/Seoul"));

    @Test
    void getIndicesFallsBackToStoredIndexSnapshotWhenRealtimeCacheIsEmpty() {
        InMemoryMarketIndexSnapshotRepository indexSnapshotRepository = new InMemoryMarketIndexSnapshotRepository();
        indexSnapshotRepository.recordLatest(indexQuote("0001", "KOSPI", "KIS_INDEX_CURRENT_PRICE"));
        indexSnapshotRepository.recordLatest(indexQuote(
                "1001",
                "KOSDAQ",
                "KOSDAQ",
                "868.41",
                "KIS_INDEX_CURRENT_PRICE"));
        indexSnapshotRepository.recordLatest(indexQuote(
                "2001",
                "KOSPI 200",
                "KOSPI200",
                "395.30",
                "KIS_INDEX_CURRENT_PRICE"));
        MarketDataService service = marketDataService(
                new InMemoryRealtimeMarketDataCache(),
                indexSnapshotRepository);

        List<MarketIndexQuote> indices = service.getIndices();

        assertThat(indices).hasSize(3);
        assertThat(indices.get(0).indexCode()).isEqualTo("0001");
        assertThat(indices.get(0).source()).isEqualTo("KIS_INDEX_CURRENT_PRICE");
    }

    @Test
    void getIndicesAllowsStoredCurrentIndexSnapshotAfterRegularSession() {
        InMemoryMarketIndexSnapshotRepository indexSnapshotRepository = new InMemoryMarketIndexSnapshotRepository();
        Instant afterClose = LocalDateTime.of(2025, 6, 3, 16, 5)
                .atZone(ZoneId.of("Asia/Seoul"))
                .toInstant();
        indexSnapshotRepository.recordLatest(indexQuote(
                "0001",
                "KOSPI",
                "KOSPI",
                "8088.34",
                afterClose,
                "KIS_INDEX_CURRENT_PRICE"));
        indexSnapshotRepository.recordLatest(indexQuote(
                "1001",
                "KOSDAQ",
                "KOSDAQ",
                "868.41",
                afterClose,
                "KIS_INDEX_CURRENT_PRICE"));
        indexSnapshotRepository.recordLatest(indexQuote(
                "2001",
                "KOSPI 200",
                "KOSPI200",
                "1299.30",
                afterClose,
                "KIS_INDEX_CURRENT_PRICE"));
        MarketDataService service = marketDataService(
                new InMemoryRealtimeMarketDataCache(),
                indexSnapshotRepository);

        List<MarketIndexQuote> indices = service.getIndices();

        assertThat(indices).hasSize(3);
        assertThat(indices).extracting(MarketIndexQuote::indexCode)
                .containsExactly("0001", "1001", "2001");
    }

    @Test
    void getIndicesUsesRealtimeCacheBeforeStoredIndexSnapshot() {
        InMemoryRealtimeMarketDataCache realtimeCache = new InMemoryRealtimeMarketDataCache();
        realtimeCache.putIndex(indexTick("0001", "KOSPI", "KIS_WEBSOCKET_INDEX"));
        realtimeCache.putIndex(indexTick(
                "1001",
                "KOSDAQ",
                "090000",
                FIXED_CLOCK.instant(),
                "KIS_WEBSOCKET_INDEX",
                "868.41"));
        realtimeCache.putIndex(indexTick(
                "2001",
                "KOSPI 200",
                "090000",
                FIXED_CLOCK.instant(),
                "KIS_WEBSOCKET_INDEX",
                "395.30"));
        InMemoryMarketIndexSnapshotRepository indexSnapshotRepository = new InMemoryMarketIndexSnapshotRepository();
        indexSnapshotRepository.recordLatest(indexQuote("0001", "KOSPI", "KIS_REALTIME_INDEX_SNAPSHOT"));
        indexSnapshotRepository.recordLatest(indexQuote(
                "1001",
                "KOSDAQ",
                "KOSDAQ",
                "868.41",
                "KIS_REALTIME_INDEX_SNAPSHOT"));
        indexSnapshotRepository.recordLatest(indexQuote(
                "2001",
                "KOSPI 200",
                "KOSPI200",
                "395.30",
                "KIS_REALTIME_INDEX_SNAPSHOT"));
        MarketDataService service = marketDataService(realtimeCache, indexSnapshotRepository);

        List<MarketIndexQuote> indices = service.getIndices();

        assertThat(indices).hasSize(3);
        assertThat(indices.get(0).indexCode()).isEqualTo("0001");
        assertThat(indices.get(0).source()).isEqualTo("KIS_WEBSOCKET_INDEX");
        assertThat(indices.get(0).currentValue()).isEqualByComparingTo("2801.50");
    }

    @Test
    void getIndicesSkipsOutOfSessionRealtimeCacheBeforeCurrentIndexQuote() {
        InMemoryRealtimeMarketDataCache realtimeCache = new InMemoryRealtimeMarketDataCache();
        realtimeCache.putIndex(indexTick(
                "0001",
                "KOSPI",
                "180500",
                Instant.parse("2025-06-04T09:05:00Z"),
                "KIS_WEBSOCKET_INDEX"));
        InMemoryMarketIndexSnapshotRepository indexSnapshotRepository = new InMemoryMarketIndexSnapshotRepository();
        KisIndexCurrentPriceClient kisIndexCurrentPriceClient = mock(KisIndexCurrentPriceClient.class);
        when(kisIndexCurrentPriceClient.findCurrentIndex("0001")).thenReturn(Optional.of(indexCurrentSnapshot()));
        when(kisIndexCurrentPriceClient.findCurrentIndex("1001"))
                .thenReturn(Optional.of(indexCurrentSnapshot("1001", "KOSDAQ", "KOSDAQ", "868.41")));
        when(kisIndexCurrentPriceClient.findCurrentIndex("2001"))
                .thenReturn(Optional.of(indexCurrentSnapshot("2001", "KOSPI 200", "KOSPI200", "395.30")));
        MarketDataService service = marketDataService(
                realtimeCache,
                indexSnapshotRepository,
                null,
                kisIndexCurrentPriceClient);

        List<MarketIndexQuote> indices = service.getIndices();

        assertThat(indices).hasSize(3);
        assertThat(indices.get(0).indexCode()).isEqualTo("0001");
        assertThat(indices.get(0).currentValue()).isEqualByComparingTo("2891.12");
        assertThat(indices.get(0).source()).isEqualTo("KIS_INDEX_CURRENT_PRICE");
    }

    @Test
    void getIndicesBuildsStoredSnapshotsFromCompleteLatestIntradayHistoryWhenIndexCacheIsEmpty() {
        InMemoryMarketIndexSnapshotRepository indexSnapshotRepository = new InMemoryMarketIndexSnapshotRepository();
        MarketIndexHistoryService indexHistoryService = mock(MarketIndexHistoryService.class);
        when(indexHistoryService.getIntradayHistory("0001", null, 390)).thenReturn(List.of(
                indexIntraday("0001", "KOSPI", "KOSPI", LocalDateTime.of(2026, 7, 2, 15, 29), "2885.00"),
                indexIntraday("0001", "KOSPI", "KOSPI", LocalDateTime.of(2026, 7, 2, 15, 30), "2890.12")));
        when(indexHistoryService.getIntradayHistory("1001", null, 390)).thenReturn(List.of(
                indexIntraday("1001", "KOSDAQ", "KOSDAQ", LocalDateTime.of(2026, 7, 2, 15, 29), "866.00"),
                indexIntraday("1001", "KOSDAQ", "KOSDAQ", LocalDateTime.of(2026, 7, 2, 15, 30), "868.41")));
        when(indexHistoryService.getIntradayHistory("2001", null, 390)).thenReturn(List.of(
                indexIntraday("2001", "KOSPI 200", "KOSPI200", LocalDateTime.of(2026, 7, 2, 15, 29), "394.20"),
                indexIntraday("2001", "KOSPI 200", "KOSPI200", LocalDateTime.of(2026, 7, 2, 15, 30), "395.30")));
        MarketDataService service = marketDataService(
                new InMemoryRealtimeMarketDataCache(),
                indexSnapshotRepository,
                indexHistoryService);

        List<MarketIndexQuote> indices = service.getIndices();

        assertThat(indices).hasSize(3);
        assertThat(indices.get(0).indexCode()).isEqualTo("0001");
        assertThat(indices.get(0).currentValue()).isEqualByComparingTo("2890.12");
        assertThat(indices.get(0).source()).isEqualTo("KIS_TIME_INDEX_CHART_PRICE_LATEST_CLOSE");
        assertThat(indexSnapshotRepository.findLatestIndices()).hasSize(3);
    }

    @Test
    void getIndicesSkipsPartialLatestIntradayFallback() {
        InMemoryMarketIndexSnapshotRepository indexSnapshotRepository = new InMemoryMarketIndexSnapshotRepository();
        MarketIndexHistoryService indexHistoryService = mock(MarketIndexHistoryService.class);
        when(indexHistoryService.getIntradayHistory("0001", null, 390)).thenReturn(List.of());
        when(indexHistoryService.getIntradayHistory("1001", null, 390)).thenReturn(List.of(
                indexIntraday("1001", "KOSDAQ", "KOSDAQ", LocalDateTime.of(2026, 7, 2, 15, 30), "868.41")));
        when(indexHistoryService.getIntradayHistory("2001", null, 390)).thenReturn(List.of());
        MarketDataService service = marketDataService(
                new InMemoryRealtimeMarketDataCache(),
                indexSnapshotRepository,
                indexHistoryService);

        List<MarketIndexQuote> indices = service.getIndices();

        assertThat(indices).isEmpty();
        assertThat(indexSnapshotRepository.findLatestIndices()).isEmpty();
    }

    @Test
    void getIndicesSkipsPartialStoredDefaultIndexSnapshot() {
        InMemoryMarketIndexSnapshotRepository indexSnapshotRepository = new InMemoryMarketIndexSnapshotRepository();
        indexSnapshotRepository.recordLatest(indexQuote(
                "1001",
                "KOSDAQ",
                "KOSDAQ",
                "868.41",
                "KIS_TIME_INDEX_CHART_PRICE_LATEST_CLOSE"));
        MarketDataService service = marketDataService(
                new InMemoryRealtimeMarketDataCache(),
                indexSnapshotRepository);

        List<MarketIndexQuote> indices = service.getIndices();

        assertThat(indices).isEmpty();
    }

    @Test
    void getIndicesSkipsPartialRealtimeDefaultIndexBatch() {
        InMemoryRealtimeMarketDataCache realtimeCache = new InMemoryRealtimeMarketDataCache();
        realtimeCache.putIndex(indexTick(
                "1001",
                "KOSDAQ",
                "090000",
                FIXED_CLOCK.instant(),
                "KIS_WEBSOCKET_INDEX",
                "868.41"));
        MarketDataService service = marketDataService(
                realtimeCache,
                new InMemoryMarketIndexSnapshotRepository());

        List<MarketIndexQuote> indices = service.getIndices();

        assertThat(indices).isEmpty();
    }

    @Test
    void getIndicesReplacesStoredIntradayFallbackWithKisCurrentIndexQuote() {
        InMemoryMarketIndexSnapshotRepository indexSnapshotRepository = new InMemoryMarketIndexSnapshotRepository();
        indexSnapshotRepository.recordLatest(indexQuote("0001", "KOSPI", "KIS_TIME_INDEX_CHART_PRICE_LATEST_CLOSE"));
        KisIndexCurrentPriceClient kisIndexCurrentPriceClient = mock(KisIndexCurrentPriceClient.class);
        when(kisIndexCurrentPriceClient.findCurrentIndex("0001")).thenReturn(Optional.of(indexCurrentSnapshot()));
        when(kisIndexCurrentPriceClient.findCurrentIndex("1001"))
                .thenReturn(Optional.of(indexCurrentSnapshot("1001", "KOSDAQ", "KOSDAQ", "868.41")));
        when(kisIndexCurrentPriceClient.findCurrentIndex("2001"))
                .thenReturn(Optional.of(indexCurrentSnapshot("2001", "KOSPI 200", "KOSPI200", "395.30")));
        MarketDataService service = marketDataService(
                new InMemoryRealtimeMarketDataCache(),
                indexSnapshotRepository,
                null,
                kisIndexCurrentPriceClient);

        List<MarketIndexQuote> indices = service.getIndices();

        assertThat(indices).hasSize(3);
        assertThat(indices.get(0).indexCode()).isEqualTo("0001");
        assertThat(indices.get(0).currentValue()).isEqualByComparingTo("2891.12");
        assertThat(indices.get(0).changeValue()).isEqualByComparingTo("-655.32");
        assertThat(indices.get(0).changeRate()).isEqualByComparingTo("-7.89");
        assertThat(indices.get(0).source()).isEqualTo("KIS_INDEX_CURRENT_PRICE");
        assertThat(indexSnapshotRepository.findLatestIndices().get(0).source())
                .isEqualTo("KIS_INDEX_CURRENT_PRICE");
    }

    @Test
    void getIndicesRejectsImplausibleCurrentIndexBatch() {
        InMemoryMarketIndexSnapshotRepository indexSnapshotRepository = new InMemoryMarketIndexSnapshotRepository();
        KisIndexCurrentPriceClient kisIndexCurrentPriceClient = mock(KisIndexCurrentPriceClient.class);
        when(kisIndexCurrentPriceClient.findCurrentIndex("0001"))
                .thenReturn(Optional.of(indexCurrentSnapshot("0001", "KOSPI", "KOSPI", "0.00")));
        when(kisIndexCurrentPriceClient.findCurrentIndex("1001"))
                .thenReturn(Optional.of(indexCurrentSnapshot("1001", "KOSDAQ", "KOSDAQ", "868.41")));
        when(kisIndexCurrentPriceClient.findCurrentIndex("2001"))
                .thenReturn(Optional.of(indexCurrentSnapshot("2001", "KOSPI 200", "KOSPI200", "0.00")));
        MarketDataService service = marketDataService(
                new InMemoryRealtimeMarketDataCache(),
                indexSnapshotRepository,
                null,
                kisIndexCurrentPriceClient);

        List<MarketIndexQuote> indices = service.getIndices();

        assertThat(indices).isEmpty();
        assertThat(indexSnapshotRepository.findLatestIndices()).isEmpty();
    }

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
        cache.put(foreignOwnershipSnapshot());
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
        assertThat(quote.source()).isEqualTo("KIS_OPEN_API+KRX_FOREIGN_OWNERSHIP_CACHE");
    }

    @Test
    void getQuoteRetriesKisCurrentPriceOnceWhenRateLimited() {
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
        cache.put(foreignOwnershipSnapshot());
        when(kisCurrentPriceClient.findCurrentPrice("005930"))
                .thenThrow(new IllegalStateException("EGW00201"))
                .thenReturn(Optional.of(kisSnapshotWithForeignOwnership()));

        MarketQuote quote = service.getQuote("005930", "USD", new BigDecimal("0.00072"));

        assertThat(quote.currentPriceKrw()).isEqualByComparingTo("81200");
        assertThat(quote.source()).isEqualTo("KIS_OPEN_API+KRX_FOREIGN_OWNERSHIP_CACHE");
    }

    @Test
    void getQuoteIgnoresKisForeignOwnershipFieldsAndUsesKrxCache() {
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
        cache.put(foreignOwnershipSnapshot());
        when(kisCurrentPriceClient.findCurrentPrice("005930")).thenReturn(Optional.of(
                new KisCurrentPriceSnapshot(
                        "005930",
                        "삼성전자",
                        new BigDecimal("81200"),
                        new BigDecimal("1.87"),
                        15_500_000L,
                        3_642_091_300L,
                        new BigDecimal("61.008777"),
                        6_718_486_073L,
                        new BigDecimal("54.21"))));

        MarketQuote quote = service.getQuote("005930", "USD", new BigDecimal("0.00072"));

        assertThat(quote.foreignOwnedQuantity()).isEqualTo(3_400_000_000L);
        assertThat(quote.foreignOwnershipRate()).isEqualByComparingTo("51.0100");
        assertThat(quote.foreignLimitExhaustionRate()).isEqualByComparingTo("50.9900");
        assertThat(quote.foreignOwnershipBaseDate()).isEqualTo(LocalDate.of(2025, 6, 2));
        assertThat(quote.source()).isEqualTo("KIS_OPEN_API+KRX_FOREIGN_OWNERSHIP_CACHE");
        assertThat(cache.find("005930")).isPresent();
    }

    @Test
    void getQuoteUsesKisForeignOwnershipWhenKrxCacheIsMissing() {
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
        when(kisCurrentPriceClient.findCurrentPrice("005930")).thenReturn(Optional.of(kisSnapshotWithForeignOwnership()));

        MarketQuote quote = service.getQuote("005930", "USD", new BigDecimal("0.00072"));

        assertThat(quote.foreignOwnedQuantity()).isEqualTo(3_642_091_300L);
        assertThat(quote.foreignOwnershipRate()).isEqualByComparingTo("61.0088");
        assertThat(quote.foreignLimitExhaustionRate()).isEqualByComparingTo("54.2100");
        assertThat(quote.foreignOwnershipBaseDate()).isEqualTo(LocalDate.of(2025, 6, 4));
        assertThat(quote.source()).isEqualTo("KIS_OPEN_API+KIS_CURRENT_PRICE_FOREIGN_OWNERSHIP");
    }

    @Test
    void getQuoteKeepsPriceWhenForeignOwnershipIsMissing() {
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
        assertThat(quote.foreignOwnedQuantity()).isZero();
        assertThat(quote.foreignOwnershipRate()).isNull();
        assertThat(quote.foreignLimitExhaustionRate()).isNull();
        assertThat(quote.foreignOwnershipBaseDate()).isNull();
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
        when(kisCurrentPriceClient.findCurrentPrice("005930")).thenReturn(Optional.empty());
        cache.put(foreignOwnershipSnapshot());
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
        assertThat(quote.foreignOwnedQuantity()).isEqualTo(3_400_000_000L);
        assertThat(quote.foreignOwnershipRate()).isEqualByComparingTo("51.01");
        assertThat(quote.foreignLimitExhaustionRate()).isEqualByComparingTo("50.99");
        assertThat(quote.source()).isEqualTo("PUBLIC_DATA_STOCK_SECURITIES+KRX_FOREIGN_OWNERSHIP_CACHE");
    }

    @Test
    void getQuoteFailsWhenProviderIsUnavailable() {
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

        assertThatThrownBy(() -> service.getQuote("005930", "USD", new BigDecimal("0.00072")))
                .isInstanceOf(MarketDataUnavailableException.class)
                .hasMessageContaining("No live provider price is available");
    }

    @Test
    void getStockDetailKeepsMasterAndOwnershipWhenQuoteProviderIsUnavailable() {
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
        cache.put(foreignOwnershipSnapshot());
        when(kisCurrentPriceClient.findCurrentPrice("005930")).thenReturn(Optional.empty());
        when(client.findPrice("005930", LocalDate.of(2025, 6, 3))).thenReturn(Optional.empty());

        StockDetail detail = service.getStockDetail("005930", "USD", null);

        assertThat(detail.stockCode()).isEqualTo("005930");
        assertThat(detail.stockName()).isEqualTo("삼성전자");
        assertThat(detail.stockNameEn()).isEqualTo("Samsung Electronics");
        assertThat(detail.market()).isEqualTo("KOSPI");
        assertThat(detail.currentPriceKrw()).isNull();
        assertThat(detail.localCurrencyPrice()).isNull();
        assertThat(detail.changeRate()).isNull();
        assertThat(detail.volume()).isZero();
        assertThat(detail.marketDataTime()).isNull();
        assertThat(detail.foreignOwnedQuantity()).isEqualTo(3_400_000_000L);
        assertThat(detail.foreignOwnershipRate()).isEqualByComparingTo("51.01");
        assertThat(detail.foreignLimitExhaustionRate()).isEqualByComparingTo("50.99");
        assertThat(detail.foreignOwnershipPredictionConfidenceLevel())
                .isEqualTo("FOREIGN_LIMIT_NOT_APPLICABLE");
        assertThat(detail.foreignOwnershipBaseDate()).isEqualTo(LocalDate.of(2025, 6, 2));
        assertThat(detail.orderable()).isTrue();
        assertThat(detail.source()).contains("QUOTE_UNAVAILABLE+KRX_FOREIGN_OWNERSHIP_CACHE");
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
        cache.put(new ForeignOwnershipSnapshot(
                "005930",
                3_400_000_000L,
                new BigDecimal("51.01"),
                6_720_000_000L,
                new BigDecimal("50.99"),
                LocalDate.of(2025, 6, 2)));

        when(repository.findByCode("005930")).thenReturn(Optional.of(stock));
        when(kisCurrentPriceClient.findCurrentPrice("005930")).thenReturn(Optional.of(
                new KisCurrentPriceSnapshot(
                        "005930",
                        "삼성전자",
                        new BigDecimal("81200"),
                        new BigDecimal("1.87"),
                        15_500_000L)));

        MarketQuote quote = service.getQuote("005930", "USD", new BigDecimal("0.00072"));

        assertThat(quote.foreignOwnedQuantity()).isEqualTo(3_400_000_000L);
        assertThat(quote.foreignOwnershipRate()).isEqualByComparingTo("51.01");
        assertThat(quote.foreignLimitExhaustionRate()).isEqualByComparingTo("50.99");
        assertThat(quote.foreignOwnershipBaseDate()).isEqualTo(LocalDate.of(2025, 6, 2));
        assertThat(quote.source()).isEqualTo("KIS_OPEN_API+KRX_FOREIGN_OWNERSHIP_CACHE");
    }

    @Test
    void getQuoteUsesCachedExchangeRateWhenRequestRateIsMissing() {
        PublicDataStockSecuritiesClient client = mock(PublicDataStockSecuritiesClient.class);
        KisCurrentPriceClient kisCurrentPriceClient = mock(KisCurrentPriceClient.class);
        StockMasterRepository repository = mock(StockMasterRepository.class);
        ExchangeRateCache exchangeRateCache = new InMemoryExchangeRateCache();
        ForeignOwnershipSnapshotCache cache = new InMemoryForeignOwnershipSnapshotCache();
        MarketDataService service = new MarketDataService(
                client,
                kisCurrentPriceClient,
                repository,
                cache,
                exchangeRateCache,
                new InMemoryRealtimeMarketDataCache(),
                FIXED_CLOCK);

        when(repository.findByCode("005930")).thenReturn(Optional.of(samsungElectronics()));
        cache.put(foreignOwnershipSnapshot());
        when(kisCurrentPriceClient.findCurrentPrice("005930")).thenReturn(Optional.of(kisSnapshotWithForeignOwnership()));
        service.updateExchangeRate("USD", new BigDecimal("0.00072"));

        MarketQuote quote = service.getQuote("005930", "USD", null);

        assertThat(quote.localCurrencyPrice()).isEqualByComparingTo("58.4640");
        assertThat(exchangeRateCache.find("USD")).isPresent();
    }

    @Test
    void getQuoteUsesRequestExchangeRateBeforeCachedRate() {
        PublicDataStockSecuritiesClient client = mock(PublicDataStockSecuritiesClient.class);
        KisCurrentPriceClient kisCurrentPriceClient = mock(KisCurrentPriceClient.class);
        StockMasterRepository repository = mock(StockMasterRepository.class);
        ExchangeRateCache exchangeRateCache = new InMemoryExchangeRateCache();
        ForeignOwnershipSnapshotCache cache = new InMemoryForeignOwnershipSnapshotCache();
        MarketDataService service = new MarketDataService(
                client,
                kisCurrentPriceClient,
                repository,
                cache,
                exchangeRateCache,
                new InMemoryRealtimeMarketDataCache(),
                FIXED_CLOCK);

        when(repository.findByCode("005930")).thenReturn(Optional.of(samsungElectronics()));
        cache.put(foreignOwnershipSnapshot());
        when(kisCurrentPriceClient.findCurrentPrice("005930")).thenReturn(Optional.of(kisSnapshotWithForeignOwnership()));
        service.updateExchangeRate("USD", new BigDecimal("0.00072"));

        MarketQuote quote = service.getQuote("005930", "USD", new BigDecimal("0.00080"));

        assertThat(quote.localCurrencyPrice()).isEqualByComparingTo("64.9600");
    }

    @Test
    void getQuoteIncludesFxMetadataForCachedExchangeRate() {
        PublicDataStockSecuritiesClient client = mock(PublicDataStockSecuritiesClient.class);
        KisCurrentPriceClient kisCurrentPriceClient = mock(KisCurrentPriceClient.class);
        StockMasterRepository repository = mock(StockMasterRepository.class);
        ExchangeRateCache exchangeRateCache = new InMemoryExchangeRateCache();
        ForeignOwnershipSnapshotCache cache = new InMemoryForeignOwnershipSnapshotCache();
        MarketDataService service = new MarketDataService(
                client,
                kisCurrentPriceClient,
                repository,
                cache,
                exchangeRateCache,
                new InMemoryRealtimeMarketDataCache(),
                FIXED_CLOCK);

        when(repository.findByCode("005930")).thenReturn(Optional.of(samsungElectronics()));
        cache.put(foreignOwnershipSnapshot());
        when(kisCurrentPriceClient.findCurrentPrice("005930")).thenReturn(Optional.of(kisSnapshotWithForeignOwnership()));
        service.updateExchangeRate("USD", new BigDecimal("0.00072"));

        MarketQuote quote = service.getQuote("005930", "USD", null);

        assertThat(quote.fxRate()).isEqualByComparingTo("0.00072");
        assertThat(quote.fxRateTime()).isEqualTo(Instant.parse("2025-06-04T00:00:00Z"));
        assertThat(quote.fxRateSource()).isEqualTo("EXCHANGE_RATE_CACHE");
        assertThat(quote.fxStale()).isFalse();
    }

    @Test
    void getQuoteFailsWhenExchangeRateIsMissingAndNoRequestRateIsProvided() {
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
        cache.put(foreignOwnershipSnapshot());
        when(kisCurrentPriceClient.findCurrentPrice("005930")).thenReturn(Optional.of(kisSnapshotWithForeignOwnership()));

        assertThatThrownBy(() -> service.getQuote("005930", "USD", null))
                .isInstanceOf(MarketDataUnavailableException.class)
                .hasMessageContaining("No FX provider or partner exchange rate");
    }

    @Test
    void getQuotesReturnsPassiveSeededStocksWithoutKisRestFanOut() {
        PublicDataStockSecuritiesClient client = mock(PublicDataStockSecuritiesClient.class);
        KisCurrentPriceClient kisCurrentPriceClient = mock(KisCurrentPriceClient.class);
        StockMasterRepository repository = mock(StockMasterRepository.class);
        ForeignOwnershipSnapshotCache cache = new InMemoryForeignOwnershipSnapshotCache();
        RealtimeMarketDataCache realtimeCache = new InMemoryRealtimeMarketDataCache();
        MarketDataService service = new MarketDataService(
                client,
                kisCurrentPriceClient,
                repository,
                cache,
                new InMemoryExchangeRateCache(),
                realtimeCache,
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
        cache.put(foreignOwnershipSnapshot("000660"));
        cache.put(foreignOwnershipSnapshot("005930"));
        realtimeCache.putTrade(new KisRealtimeTradeTick(
                "000660",
                "093000",
                new BigDecimal("510000"),
                new BigDecimal("1.25"),
                new BigDecimal("511000"),
                new BigDecimal("509000"),
                900L,
                4_000_000L,
                LocalDate.of(2025, 6, 4)));
        realtimeCache.putTrade(new KisRealtimeTradeTick(
                "005930",
                "093001",
                new BigDecimal("81500"),
                new BigDecimal("1.92"),
                new BigDecimal("81600"),
                new BigDecimal("81400"),
                1200L,
                16_200_000L,
                LocalDate.of(2025, 6, 4)));

        List<MarketQuote> quotes = service.getQuotes(List.of(), "KOSPI", "USD", new BigDecimal("0.00072"), 10);

        assertThat(quotes).extracting(MarketQuote::stockCode).containsExactly("000660", "005930");
        assertThat(quotes).extracting(MarketQuote::fxRateSource).containsOnly("PARTNER_REQUEST");
        verifyNoInteractions(kisCurrentPriceClient);
    }

    @Test
    void getQuotesDeduplicatesRequestedStockCodesAndPreservesOrder() {
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
        StockSummary skHynix = new StockSummary(
                "000660",
                "SK하이닉스",
                "SK hynix",
                "KOSPI",
                "KR7000660001",
                "00164779");

        when(repository.findByCode("000660")).thenReturn(Optional.of(skHynix));
        when(repository.findByCode("005930")).thenReturn(Optional.of(samsungElectronics()));
        cache.put(foreignOwnershipSnapshot("000660"));
        cache.put(foreignOwnershipSnapshot("005930"));
        when(kisCurrentPriceClient.findCurrentPrice("000660")).thenReturn(Optional.of(kisSnapshotWithForeignOwnership("000660")));
        when(kisCurrentPriceClient.findCurrentPrice("005930")).thenReturn(Optional.of(kisSnapshotWithForeignOwnership("005930")));

        List<MarketQuote> quotes = service.getQuotes(
                List.of("000660", "005930", "000660"),
                null,
                "USD",
                new BigDecimal("0.00072"),
                10);

        assertThat(quotes).extracting(MarketQuote::stockCode).containsExactly("000660", "005930");
    }

    @Test
    void getQuotesFallsBackToStoredIntradayWhenKisCurrentPriceIsUnavailable() {
        PublicDataStockSecuritiesClient client = mock(PublicDataStockSecuritiesClient.class);
        KisCurrentPriceClient kisCurrentPriceClient = mock(KisCurrentPriceClient.class);
        StockMasterRepository repository = mock(StockMasterRepository.class);
        MarketIntradayPriceRepository intradayRepository = mock(MarketIntradayPriceRepository.class);
        MarketDailyPriceRepository dailyRepository = mock(MarketDailyPriceRepository.class);
        ForeignOwnershipSnapshotCache cache = new InMemoryForeignOwnershipSnapshotCache();
        MarketDataService service = new MarketDataService(
                client,
                kisCurrentPriceClient,
                null,
                null,
                repository,
                cache,
                new InMemoryForeignOwnershipDailySnapshotRepository(),
                new InMemoryForeignOwnershipPredictionCache(),
                new InMemoryExchangeRateCache(),
                new InMemoryRealtimeMarketDataCache(),
                new InMemoryMarketIndexSnapshotRepository(),
                null,
                intradayRepository,
                dailyRepository,
                null,
                new ForeignOwnershipPredictionEngine(FIXED_CLOCK),
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
        when(kisCurrentPriceClient.findCurrentPrice("000660"))
                .thenThrow(new IllegalStateException("EGW00201"));
        when(intradayRepository.findLatestByStockCodeAndDate("000660", LocalDate.of(2025, 6, 4)))
                .thenReturn(Optional.of(new MarketIntradayPrice(
                        "000660",
                        LocalDateTime.of(2025, 6, 4, 15, 30),
                        "KOSPI",
                        new BigDecimal("509000"),
                        new BigDecimal("510000"),
                        new BigDecimal("508000"),
                        new BigDecimal("510000"),
                        4_000_000L,
                        new BigDecimal("2040000000000"),
                        "KIS_TIME_CHART_PRICE",
                        FIXED_CLOCK.instant())));
        when(dailyRepository.findLatestBefore("000660", LocalDate.of(2025, 6, 4)))
                .thenReturn(Optional.of(new MarketDailyPrice(
                        "000660",
                        LocalDate.of(2025, 6, 3),
                        "KOSPI",
                        new BigDecimal("500000"),
                        new BigDecimal("502000"),
                        new BigDecimal("498000"),
                        new BigDecimal("500000"),
                        BigDecimal.ZERO,
                        3_000_000L,
                        new BigDecimal("1500000000000"),
                        new BigDecimal("500000"),
                        "KRX_DAILY",
                        FIXED_CLOCK.instant())));

        List<MarketQuote> quotes = service.getQuotes(
                List.of("000660", "005930"),
                "KOSPI",
                "USD",
                new BigDecimal("0.00072"),
                10);

        assertThat(quotes)
                .extracting(MarketQuote::stockCode)
                .containsExactly("000660");
        assertThat(quotes.get(0).source()).contains("KIS_INTRADAY_PRICE_SNAPSHOT");
    }

    @Test
    void getQuoteUsesRealtimeTradeCacheBeforeKisRest() {
        PublicDataStockSecuritiesClient client = mock(PublicDataStockSecuritiesClient.class);
        KisCurrentPriceClient kisCurrentPriceClient = mock(KisCurrentPriceClient.class);
        StockMasterRepository repository = mock(StockMasterRepository.class);
        ForeignOwnershipSnapshotCache cache = new InMemoryForeignOwnershipSnapshotCache();
        RealtimeMarketDataCache realtimeCache = new InMemoryRealtimeMarketDataCache();
        MarketDataService service = new MarketDataService(
                client,
                kisCurrentPriceClient,
                repository,
                cache,
                new InMemoryExchangeRateCache(),
                realtimeCache,
                FIXED_CLOCK);

        when(repository.findByCode("005930")).thenReturn(Optional.of(samsungElectronics()));
        cache.put(foreignOwnershipSnapshot());
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
        assertThat(quote.source()).isEqualTo("KIS_WEBSOCKET_TRADE+KRX_FOREIGN_OWNERSHIP_CACHE");
    }

    @Test
    void getQuoteUsesStoredIntradaySnapshotWhenKisRestReturnsNoCurrentPrice() {
        PublicDataStockSecuritiesClient client = mock(PublicDataStockSecuritiesClient.class);
        KisCurrentPriceClient kisCurrentPriceClient = mock(KisCurrentPriceClient.class);
        StockMasterRepository repository = mock(StockMasterRepository.class);
        MarketIntradayPriceRepository intradayPriceRepository = mock(MarketIntradayPriceRepository.class);
        MarketDailyPriceRepository dailyPriceRepository = mock(MarketDailyPriceRepository.class);
        ForeignOwnershipSnapshotCache cache = new InMemoryForeignOwnershipSnapshotCache();
        MarketDataService service = new MarketDataService(
                client,
                kisCurrentPriceClient,
                null,
                null,
                repository,
                cache,
                new InMemoryForeignOwnershipDailySnapshotRepository(),
                new InMemoryForeignOwnershipPredictionCache(),
                new InMemoryExchangeRateCache(),
                new InMemoryRealtimeMarketDataCache(),
                new InMemoryMarketIndexSnapshotRepository(),
                null,
                intradayPriceRepository,
                dailyPriceRepository,
                null,
                new ForeignOwnershipPredictionEngine(FIXED_CLOCK),
                FIXED_CLOCK);

        when(repository.findByCode("005930")).thenReturn(Optional.of(samsungElectronics()));
        cache.put(foreignOwnershipSnapshot());
        when(kisCurrentPriceClient.findCurrentPrice("005930")).thenReturn(Optional.empty());
        when(intradayPriceRepository.findLatestByStockCodeAndDate("005930", LocalDate.of(2025, 6, 4)))
                .thenReturn(Optional.of(new MarketIntradayPrice(
                        "005930",
                        LocalDateTime.of(2025, 6, 4, 10, 46),
                        "KOSPI",
                        new BigDecimal("81200"),
                        new BigDecimal("81500"),
                        new BigDecimal("81100"),
                        new BigDecimal("81400"),
                        12_000L,
                        new BigDecimal("976800000"),
                        "KIS_REALTIME_TRADE",
                        FIXED_CLOCK.instant())));
        when(dailyPriceRepository.findLatestBefore("005930", LocalDate.of(2025, 6, 4)))
                .thenReturn(Optional.of(dailyPrice(
                        "005930",
                        LocalDate.of(2025, 6, 3),
                        "80000")));

        MarketQuote quote = service.getQuote("005930", "USD", new BigDecimal("0.00072"));

        assertThat(quote.currentPriceKrw()).isEqualByComparingTo("81400");
        assertThat(quote.changeRate()).isEqualByComparingTo("1.7500");
        assertThat(quote.volume()).isZero();
        assertThat(quote.source()).isEqualTo("KIS_INTRADAY_PRICE_SNAPSHOT+KRX_FOREIGN_OWNERSHIP_CACHE");
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
    void getOrderBookFallsBackToKisRestOrderBookWhenRealtimeCacheIsEmpty() {
        PublicDataStockSecuritiesClient client = mock(PublicDataStockSecuritiesClient.class);
        KisCurrentPriceClient kisCurrentPriceClient = mock(KisCurrentPriceClient.class);
        KisRestOrderBookClient kisRestOrderBookClient = mock(KisRestOrderBookClient.class);
        StockMasterRepository repository = mock(StockMasterRepository.class);
        MarketDataService service = new MarketDataService(
                client,
                kisCurrentPriceClient,
                kisRestOrderBookClient,
                repository,
                new InMemoryForeignOwnershipSnapshotCache(),
                new InMemoryExchangeRateCache(),
                new InMemoryRealtimeMarketDataCache(),
                FIXED_CLOCK);

        when(kisRestOrderBookClient.findOrderBook("005930")).thenReturn(Optional.of(new KisRestOrderBookSnapshot(
                "005930",
                List.of(new KisRestOrderBookSnapshot.Level(new BigDecimal("81600"), 1200L)),
                List.of(new KisRestOrderBookSnapshot.Level(new BigDecimal("81400"), 1800L)))));

        OrderBook orderBook = service.getOrderBook("005930");

        assertThat(orderBook.asks()).hasSize(1);
        assertThat(orderBook.asks().get(0).priceKrw()).isEqualByComparingTo("81600");
        assertThat(orderBook.bids().get(0).quantity()).isEqualTo(1800L);
        assertThat(orderBook.source()).isEqualTo("KIS_REST_ORDERBOOK");
    }

    @Test
    void getOrderBookRetriesKisRestOrderBookOnceWhenRateLimited() {
        PublicDataStockSecuritiesClient client = mock(PublicDataStockSecuritiesClient.class);
        KisCurrentPriceClient kisCurrentPriceClient = mock(KisCurrentPriceClient.class);
        KisRestOrderBookClient kisRestOrderBookClient = mock(KisRestOrderBookClient.class);
        StockMasterRepository repository = mock(StockMasterRepository.class);
        MarketDataService service = new MarketDataService(
                client,
                kisCurrentPriceClient,
                kisRestOrderBookClient,
                repository,
                new InMemoryForeignOwnershipSnapshotCache(),
                new InMemoryExchangeRateCache(),
                new InMemoryRealtimeMarketDataCache(),
                FIXED_CLOCK);

        when(kisRestOrderBookClient.findOrderBook("005930"))
                .thenThrow(new IllegalStateException("EGW00201"))
                .thenReturn(Optional.of(new KisRestOrderBookSnapshot(
                        "005930",
                        List.of(new KisRestOrderBookSnapshot.Level(new BigDecimal("81600"), 1200L)),
                        List.of(new KisRestOrderBookSnapshot.Level(new BigDecimal("81400"), 1800L)))));

        OrderBook orderBook = service.getOrderBook("005930");

        assertThat(orderBook.source()).isEqualTo("KIS_REST_ORDERBOOK");
        assertThat(orderBook.asks().get(0).priceKrw()).isEqualByComparingTo("81600");
    }

    @Test
    void getOrderBookFailsWhenRealtimeAndRestProvidersAreUnavailable() {
        PublicDataStockSecuritiesClient client = mock(PublicDataStockSecuritiesClient.class);
        KisCurrentPriceClient kisCurrentPriceClient = mock(KisCurrentPriceClient.class);
        KisRestOrderBookClient kisRestOrderBookClient = mock(KisRestOrderBookClient.class);
        StockMasterRepository repository = mock(StockMasterRepository.class);
        MarketDataService service = new MarketDataService(
                client,
                kisCurrentPriceClient,
                kisRestOrderBookClient,
                repository,
                new InMemoryForeignOwnershipSnapshotCache(),
                new InMemoryExchangeRateCache(),
                new InMemoryRealtimeMarketDataCache(),
                FIXED_CLOCK);

        when(kisRestOrderBookClient.findOrderBook("005930")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getOrderBook("005930"))
                .isInstanceOf(MarketDataUnavailableException.class)
                .hasMessageContaining("No KIS order book provider data");
    }

    @Test
    void getOrderabilityWarnsBuyWhenForecastForeignLimitMayBeReached() {
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
        cache.put(new ForeignOwnershipSnapshot(
                "015760",
                1_000L,
                new BigDecimal("50.00"),
                1_000L,
                new BigDecimal("100.0000"),
                LocalDate.of(2025, 6, 3)));

        when(repository.findByCode("015760")).thenReturn(Optional.of(foreignLimitRestrictedStock()));
        when(kisCurrentPriceClient.findCurrentPrice("015760")).thenReturn(Optional.empty());
        when(client.findPrice("015760", LocalDate.of(2025, 6, 3))).thenReturn(Optional.empty());

        Orderability orderability = service.getOrderability("015760", "BUY", 20);

        assertThat(orderability.orderable()).isTrue();
        assertThat(orderability.orderBlockedReason()).isNull();
        assertThat(orderability.foreignLimitExceeded()).isTrue();
        assertThat(orderability.currentForeignLimitExhaustionRate()).isEqualByComparingTo("100.0000");
        assertThat(orderability.predictedForeignLimitExhaustionRate()).isEqualByComparingTo("100.000000");
        assertThat(orderability.foreignOwnershipPrediction().minForeignLimitExhaustionRate())
                .isEqualByComparingTo("99.950000");
        assertThat(orderability.foreignOwnershipPrediction().baseForeignLimitExhaustionRate())
                .isEqualByComparingTo("100.000000");
        assertThat(orderability.foreignOwnershipPrediction().maxForeignLimitExhaustionRate())
                .isEqualByComparingTo("100.050000");
        assertThat(orderability.foreignOwnershipPrediction().confidenceLevel()).isEqualTo("SNAPSHOT_ONLY");
        assertThat(orderability.foreignOwnershipBaseDate()).isEqualTo(LocalDate.of(2025, 6, 3));
        assertThat(orderability.priceLimitState()).isEqualTo("UNKNOWN");
        assertThat(orderability.viActive()).isFalse();
        assertThat(orderability.source())
                .isEqualTo("ORDERABILITY_QUOTE_UNAVAILABLE+KRX_FOREIGN_OWNERSHIP_CACHE+MARKET_STATUS_UNAVAILABLE");
    }

    @Test
    void getOrderabilityUsesRestOrderBookForPriceLimitStatusWhenRealtimeStatusIsUnavailable() {
        PublicDataStockSecuritiesClient client = mock(PublicDataStockSecuritiesClient.class);
        KisCurrentPriceClient kisCurrentPriceClient = mock(KisCurrentPriceClient.class);
        KisRestOrderBookClient kisRestOrderBookClient = mock(KisRestOrderBookClient.class);
        StockMasterRepository repository = mock(StockMasterRepository.class);
        ForeignOwnershipSnapshotCache cache = new InMemoryForeignOwnershipSnapshotCache();
        MarketDataService service = new MarketDataService(
                client,
                kisCurrentPriceClient,
                kisRestOrderBookClient,
                repository,
                cache,
                new InMemoryExchangeRateCache(),
                new InMemoryRealtimeMarketDataCache(),
                FIXED_CLOCK);
        cache.put(foreignOwnershipSnapshot());

        when(repository.findByCode("005930")).thenReturn(Optional.of(samsungElectronics()));
        when(kisCurrentPriceClient.findCurrentPrice("005930")).thenReturn(Optional.empty());
        when(client.findPrice("005930", LocalDate.of(2025, 6, 3))).thenReturn(Optional.empty());
        when(kisRestOrderBookClient.findOrderBook("005930")).thenReturn(Optional.of(new KisRestOrderBookSnapshot(
                "005930",
                List.of(new KisRestOrderBookSnapshot.Level(BigDecimal.ZERO, 0L)),
                List.of(new KisRestOrderBookSnapshot.Level(new BigDecimal("81500"), 1800L)))));

        Orderability orderability = service.getOrderability("005930", "BUY", 1);

        assertThat(orderability.priceLimitState()).isEqualTo("UPPER_LIMIT");
        assertThat(orderability.viActive()).isFalse();
        assertThat(orderability.source())
                .isEqualTo("ORDERABILITY_QUOTE_UNAVAILABLE+KRX_FOREIGN_OWNERSHIP_CACHE+KIS_REST_ORDERBOOK_STATUS_FALLBACK");
    }

    @Test
    void getOrderabilityKeepsTimeSeriesRiskAsPredictionWithoutBlocking() {
        PublicDataStockSecuritiesClient client = mock(PublicDataStockSecuritiesClient.class);
        KisCurrentPriceClient kisCurrentPriceClient = mock(KisCurrentPriceClient.class);
        StockMasterRepository repository = mock(StockMasterRepository.class);
        ForeignOwnershipSnapshotCache cache = new InMemoryForeignOwnershipSnapshotCache();
        InMemoryForeignOwnershipDailySnapshotRepository dailySnapshotRepository =
                new InMemoryForeignOwnershipDailySnapshotRepository();
        MarketDataService service = new MarketDataService(
                client,
                kisCurrentPriceClient,
                null,
                repository,
                cache,
                dailySnapshotRepository,
                new InMemoryExchangeRateCache(),
                new InMemoryRealtimeMarketDataCache(),
                null,
                new ForeignOwnershipPredictionEngine(FIXED_CLOCK),
                FIXED_CLOCK);
        cache.put(new ForeignOwnershipSnapshot(
                "015760",
                995L,
                new BigDecimal("49.75"),
                1_000L,
                new BigDecimal("99.5000"),
                LocalDate.of(2025, 6, 4)));
        dailySnapshotRepository.upsert(foreignOwnershipDailySnapshot("015760", LocalDate.of(2025, 5, 31), "98.0000"));
        dailySnapshotRepository.upsert(foreignOwnershipDailySnapshot("015760", LocalDate.of(2025, 6, 1), "98.5000"));
        dailySnapshotRepository.upsert(foreignOwnershipDailySnapshot("015760", LocalDate.of(2025, 6, 2), "98.9000"));
        dailySnapshotRepository.upsert(foreignOwnershipDailySnapshot("015760", LocalDate.of(2025, 6, 3), "99.2000"));
        dailySnapshotRepository.upsert(foreignOwnershipDailySnapshot("015760", LocalDate.of(2025, 6, 4), "99.5000"));

        when(repository.findByCode("015760")).thenReturn(Optional.of(foreignLimitRestrictedStock()));
        when(kisCurrentPriceClient.findCurrentPrice("015760")).thenReturn(Optional.empty());
        when(client.findPrice("015760", LocalDate.of(2025, 6, 3))).thenReturn(Optional.empty());

        Orderability orderability = service.getOrderability("015760", "BUY", 1);

        assertThat(orderability.predictedForeignLimitExhaustionRate()).isEqualByComparingTo("99.875000");
        assertThat(orderability.foreignOwnershipPrediction().maxForeignLimitExhaustionRate())
                .isGreaterThan(new BigDecimal("100.0000"));
        assertThat(orderability.foreignOwnershipPrediction().confidenceLevel()).isEqualTo("TIME_SERIES_ADJUSTED");
        assertThat(orderability.foreignLimitExceeded()).isTrue();
        assertThat(orderability.orderable()).isTrue();
    }

    @Test
    void getOrderabilityUsesHannahAiForeignOwnershipPredictionWhenAvailable() {
        PublicDataStockSecuritiesClient client = mock(PublicDataStockSecuritiesClient.class);
        KisCurrentPriceClient kisCurrentPriceClient = mock(KisCurrentPriceClient.class);
        StockMasterRepository repository = mock(StockMasterRepository.class);
        HannahAiForeignOwnershipPredictionClient hannahClient =
                mock(HannahAiForeignOwnershipPredictionClient.class);
        ForeignOwnershipSnapshotCache cache = new InMemoryForeignOwnershipSnapshotCache();
        InMemoryForeignOwnershipDailySnapshotRepository dailySnapshotRepository =
                new InMemoryForeignOwnershipDailySnapshotRepository();
        MarketDataService service = new MarketDataService(
                client,
                kisCurrentPriceClient,
                null,
                repository,
                cache,
                dailySnapshotRepository,
                new InMemoryExchangeRateCache(),
                new InMemoryRealtimeMarketDataCache(),
                hannahClient,
                new ForeignOwnershipPredictionEngine(FIXED_CLOCK),
                FIXED_CLOCK);
        cache.put(new ForeignOwnershipSnapshot(
                "015760",
                995L,
                new BigDecimal("49.75"),
                1_000L,
                new BigDecimal("99.5000"),
                LocalDate.of(2025, 6, 4)));
        dailySnapshotRepository.upsert(foreignOwnershipDailySnapshot("015760", LocalDate.of(2025, 6, 3), "99.2000"));
        dailySnapshotRepository.upsert(foreignOwnershipDailySnapshot("015760", LocalDate.of(2025, 6, 4), "99.5000"));

        when(repository.findByCode("015760")).thenReturn(Optional.of(foreignLimitRestrictedStock()));
        when(kisCurrentPriceClient.findCurrentPrice("015760")).thenReturn(Optional.empty());
        when(client.findPrice("015760", LocalDate.of(2025, 6, 3))).thenReturn(Optional.empty());
        when(hannahClient.predict(any())).thenReturn(new HannahAiForeignOwnershipPredictionResponse(
                "015760",
                995L,
                990L,
                1_000L,
                0L,
                1_000L,
                999L,
                1_001L,
                new BigDecimal("99.475000"),
                new BigDecimal("99.975000"),
                new BigDecimal("100.475000"),
                new BigDecimal("0.100000"),
                new BigDecimal("0.500000"),
                0L,
                new BigDecimal("0.375000"),
                2,
                1,
                LocalDate.of(2025, 6, 4),
                FIXED_CLOCK.instant(),
                "AI_LIMITED_TIME_SERIES",
                new BigDecimal("0.6200"),
                "hannah-foreign-ownership-timeseries-v1",
                "HANNAH_MONTANA_AI_FOREIGN_OWNERSHIP+DAILY_TIMESERIES"));

        Orderability orderability = service.getOrderability("015760", "BUY", 1);

        assertThat(orderability.predictedForeignLimitExhaustionRate()).isEqualByComparingTo("99.975000");
        assertThat(orderability.foreignLimitExceeded()).isTrue();
        assertThat(orderability.orderable()).isTrue();
        assertThat(orderability.foreignOwnershipPrediction().modelVersion())
                .isEqualTo("hannah-foreign-ownership-timeseries-v1");
        assertThat(orderability.foreignOwnershipPrediction().confidenceLevel())
                .isEqualTo("AI_LIMITED_TIME_SERIES");
        assertThat(orderability.foreignOwnershipPrediction().maxForeignLimitExhaustionRate())
                .isGreaterThan(new BigDecimal("100.0000"));
    }

    @Test
    void getOrderabilityUsesPrecomputedForeignOwnershipPredictionCacheFirst() {
        PublicDataStockSecuritiesClient client = mock(PublicDataStockSecuritiesClient.class);
        KisCurrentPriceClient kisCurrentPriceClient = mock(KisCurrentPriceClient.class);
        StockMasterRepository repository = mock(StockMasterRepository.class);
        HannahAiForeignOwnershipPredictionClient hannahClient =
                mock(HannahAiForeignOwnershipPredictionClient.class);
        ForeignOwnershipSnapshotCache snapshotCache = new InMemoryForeignOwnershipSnapshotCache();
        InMemoryForeignOwnershipDailySnapshotRepository dailySnapshotRepository =
                new InMemoryForeignOwnershipDailySnapshotRepository();
        ForeignOwnershipPredictionCache predictionCache =
                new InMemoryForeignOwnershipPredictionCache();
        MarketDataService service = new MarketDataService(
                client,
                kisCurrentPriceClient,
                null,
                repository,
                snapshotCache,
                dailySnapshotRepository,
                predictionCache,
                new InMemoryExchangeRateCache(),
                new InMemoryRealtimeMarketDataCache(),
                hannahClient,
                new ForeignOwnershipPredictionEngine(FIXED_CLOCK),
                FIXED_CLOCK);
        snapshotCache.put(new ForeignOwnershipSnapshot(
                "015760",
                995L,
                new BigDecimal("49.75"),
                1_000L,
                new BigDecimal("99.5000"),
                LocalDate.of(2025, 6, 4)));
        predictionCache.put("015760", new ForeignOwnershipPrediction(
                new BigDecimal("99.400000"),
                new BigDecimal("99.800000"),
                new BigDecimal("100.200000"),
                BigDecimal.ZERO.setScale(6),
                BigDecimal.ZERO.setScale(6),
                0L,
                BigDecimal.ZERO.setScale(6),
                30,
                45,
                LocalDate.of(2025, 6, 4),
                FIXED_CLOCK.instant(),
                "AI_FOREIGN_OWNED_QUANTITY_PRECOMPUTED",
                new BigDecimal("0.8600"),
                "hannah-foreign-owned-quantity-ml-v1",
                "HANNAH_MONTANA_AI_FOREIGN_OWNED_QUANTITY_ML+CACHE"));

        when(repository.findByCode("015760")).thenReturn(Optional.of(foreignLimitRestrictedStock()));
        when(kisCurrentPriceClient.findCurrentPrice("015760")).thenReturn(Optional.empty());
        when(client.findPrice("015760", LocalDate.of(2025, 6, 3))).thenReturn(Optional.empty());

        Orderability orderability = service.getOrderability("015760", "BUY", 1);

        assertThat(orderability.predictedForeignLimitExhaustionRate()).isEqualByComparingTo("99.800000");
        assertThat(orderability.foreignLimitExceeded()).isTrue();
        assertThat(orderability.foreignOwnershipPrediction().source())
                .isEqualTo("HANNAH_MONTANA_AI_FOREIGN_OWNED_QUANTITY_ML+CACHE");
        verifyNoInteractions(hannahClient);
    }

    @Test
    void getOrderabilitySkipsHannahAiForUnrestrictedForeignLimitStock() {
        PublicDataStockSecuritiesClient client = mock(PublicDataStockSecuritiesClient.class);
        KisCurrentPriceClient kisCurrentPriceClient = mock(KisCurrentPriceClient.class);
        StockMasterRepository repository = mock(StockMasterRepository.class);
        HannahAiForeignOwnershipPredictionClient hannahClient =
                mock(HannahAiForeignOwnershipPredictionClient.class);
        ForeignOwnershipSnapshotCache cache = new InMemoryForeignOwnershipSnapshotCache();
        MarketDataService service = new MarketDataService(
                client,
                kisCurrentPriceClient,
                null,
                repository,
                cache,
                new InMemoryForeignOwnershipDailySnapshotRepository(),
                new InMemoryExchangeRateCache(),
                new InMemoryRealtimeMarketDataCache(),
                hannahClient,
                new ForeignOwnershipPredictionEngine(FIXED_CLOCK),
                FIXED_CLOCK);
        cache.put(new ForeignOwnershipSnapshot(
                "005930",
                1_000L,
                new BigDecimal("50.0000"),
                2_000L,
                new BigDecimal("50.0000"),
                LocalDate.of(2025, 6, 4)));

        when(repository.findByCode("005930")).thenReturn(Optional.of(samsungElectronics()));
        when(kisCurrentPriceClient.findCurrentPrice("005930")).thenReturn(Optional.empty());
        when(client.findPrice("005930", LocalDate.of(2025, 6, 3))).thenReturn(Optional.empty());

        Orderability orderability = service.getOrderability("005930", "BUY", 1);

        assertThat(orderability.foreignLimitExceeded()).isFalse();
        assertThat(orderability.orderable()).isTrue();
        assertThat(orderability.foreignOwnershipPrediction().confidenceLevel())
                .isEqualTo("FOREIGN_LIMIT_NOT_APPLICABLE");
        verifyNoInteractions(hannahClient);
    }

    @Test
    void getOrderabilityWarnsWithoutHannahAiForZeroForeignLimitStock() {
        PublicDataStockSecuritiesClient client = mock(PublicDataStockSecuritiesClient.class);
        KisCurrentPriceClient kisCurrentPriceClient = mock(KisCurrentPriceClient.class);
        StockMasterRepository repository = mock(StockMasterRepository.class);
        HannahAiForeignOwnershipPredictionClient hannahClient =
                mock(HannahAiForeignOwnershipPredictionClient.class);
        ForeignOwnershipSnapshotCache cache = new InMemoryForeignOwnershipSnapshotCache();
        MarketDataService service = new MarketDataService(
                client,
                kisCurrentPriceClient,
                null,
                repository,
                cache,
                new InMemoryForeignOwnershipDailySnapshotRepository(),
                new InMemoryExchangeRateCache(),
                new InMemoryRealtimeMarketDataCache(),
                hannahClient,
                new ForeignOwnershipPredictionEngine(FIXED_CLOCK),
                FIXED_CLOCK);
        cache.put(new ForeignOwnershipSnapshot(
                "034120",
                0L,
                BigDecimal.ZERO.setScale(4),
                0L,
                BigDecimal.ZERO.setScale(4),
                LocalDate.of(2025, 6, 4)));

        when(repository.findByCode("034120")).thenReturn(Optional.of(new StockSummary(
                "034120",
                "SBS",
                "SBS",
                "KOSPI",
                "KR7034120006",
                "00130772")));
        when(kisCurrentPriceClient.findCurrentPrice("034120")).thenReturn(Optional.empty());
        when(client.findPrice("034120", LocalDate.of(2025, 6, 3))).thenReturn(Optional.empty());

        Orderability orderability = service.getOrderability("034120", "BUY", 1);

        assertThat(orderability.foreignLimitExceeded()).isTrue();
        assertThat(orderability.orderable()).isTrue();
        assertThat(orderability.foreignOwnershipPrediction().confidenceLevel())
                .isEqualTo("FOREIGN_LIMIT_ZERO_NOT_ACQUIRABLE");
        assertThat(orderability.foreignOwnershipPrediction().modelVersion())
                .isEqualTo("foreign-ownership-zero-limit-v1");
        verifyNoInteractions(hannahClient);
    }

    @Test
    void getOrderabilityFallsBackToInternalPredictionWhenHannahAiFails() {
        PublicDataStockSecuritiesClient client = mock(PublicDataStockSecuritiesClient.class);
        KisCurrentPriceClient kisCurrentPriceClient = mock(KisCurrentPriceClient.class);
        StockMasterRepository repository = mock(StockMasterRepository.class);
        HannahAiForeignOwnershipPredictionClient hannahClient =
                mock(HannahAiForeignOwnershipPredictionClient.class);
        ForeignOwnershipSnapshotCache cache = new InMemoryForeignOwnershipSnapshotCache();
        MarketDataService service = new MarketDataService(
                client,
                kisCurrentPriceClient,
                null,
                repository,
                cache,
                new InMemoryForeignOwnershipDailySnapshotRepository(),
                new InMemoryExchangeRateCache(),
                new InMemoryRealtimeMarketDataCache(),
                hannahClient,
                new ForeignOwnershipPredictionEngine(FIXED_CLOCK),
                FIXED_CLOCK);
        cache.put(new ForeignOwnershipSnapshot(
                "015760",
                995L,
                new BigDecimal("49.75"),
                1_000L,
                new BigDecimal("99.5000"),
                LocalDate.of(2025, 6, 4)));

        when(repository.findByCode("015760")).thenReturn(Optional.of(foreignLimitRestrictedStock()));
        when(kisCurrentPriceClient.findCurrentPrice("015760")).thenReturn(Optional.empty());
        when(client.findPrice("015760", LocalDate.of(2025, 6, 3))).thenReturn(Optional.empty());
        when(hannahClient.predict(any())).thenThrow(new RestClientException("hannah unavailable"));

        Orderability orderability = service.getOrderability("015760", "BUY", 1);

        assertThat(orderability.orderable()).isTrue();
        assertThat(orderability.foreignOwnershipPrediction().modelVersion())
                .isEqualTo("foreign-ownership-timeseries-v1");
        assertThat(orderability.foreignOwnershipPrediction().confidenceLevel()).isEqualTo("SNAPSHOT_ONLY");
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
        cache.put(new ForeignOwnershipSnapshot(
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
        assertThat(orderability.foreignOwnershipPrediction().baseForeignLimitExhaustionRate())
                .isEqualByComparingTo("100.000000");
        assertThat(orderability.foreignOwnershipPrediction().orderImpactRate()).isEqualByComparingTo("0.000000");
    }

    @Test
    void getOrderabilityDetectsUpperLimitFromRealtimeTradeQuoteGap() {
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
        realtimeCache.putTrade(new KisRealtimeTradeTick(
                "005930",
                "093000",
                new BigDecimal("81500"),
                new BigDecimal("29.95"),
                BigDecimal.ZERO,
                new BigDecimal("81500"),
                1200L,
                16_200_000L,
                LocalDate.of(2025, 6, 4)));

        when(repository.findByCode("005930")).thenReturn(Optional.of(samsungElectronics()));

        Orderability orderability = service.getOrderability("005930", "BUY", 1);

        assertThat(orderability.orderable()).isTrue();
        assertThat(orderability.priceLimitState()).isEqualTo("UPPER_LIMIT");
        assertThat(orderability.viActive()).isFalse();
        assertThat(orderability.singlePriceTrading()).isFalse();
        assertThat(orderability.tradingHalted()).isFalse();
        assertThat(orderability.source()).isEqualTo("ORDERABILITY_KIS_WEBSOCKET_TRADE+KIS_WEBSOCKET_TRADE_STATUS");
    }

    @Test
    void getOrderabilityDetectsLowerLimitFromRealtimeTradeQuoteGap() {
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
        realtimeCache.putTrade(new KisRealtimeTradeTick(
                "005930",
                "093000",
                new BigDecimal("81500"),
                new BigDecimal("-29.95"),
                new BigDecimal("81500"),
                BigDecimal.ZERO,
                1200L,
                16_200_000L,
                LocalDate.of(2025, 6, 4)));

        when(repository.findByCode("005930")).thenReturn(Optional.of(samsungElectronics()));

        Orderability orderability = service.getOrderability("005930", "SELL", 1);

        assertThat(orderability.orderable()).isTrue();
        assertThat(orderability.priceLimitState()).isEqualTo("LOWER_LIMIT");
        assertThat(orderability.source()).isEqualTo("ORDERABILITY_KIS_WEBSOCKET_TRADE+KIS_WEBSOCKET_TRADE_STATUS");
    }

    @Test
    void getOrderabilityDetectsViAndSinglePriceFromRealtimeTradeStatus() {
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
        realtimeCache.putTrade(new KisRealtimeTradeTick(
                "005930",
                "093000",
                new BigDecimal("81500"),
                new BigDecimal("1.25"),
                new BigDecimal("81600"),
                new BigDecimal("81400"),
                1200L,
                16_200_000L,
                LocalDate.of(2025, 6, 4),
                "Y",
                "SINGLE_PRICE",
                "0"));

        when(repository.findByCode("005930")).thenReturn(Optional.of(samsungElectronics()));

        Orderability orderability = service.getOrderability("005930", "BUY", 1);

        assertThat(orderability.orderable()).isTrue();
        assertThat(orderability.viActive()).isTrue();
        assertThat(orderability.singlePriceTrading()).isTrue();
        assertThat(orderability.priceLimitState()).isEqualTo("NORMAL");
        assertThat(orderability.tradingHalted()).isFalse();
        assertThat(orderability.source()).isEqualTo("ORDERABILITY_KIS_WEBSOCKET_TRADE+KIS_WEBSOCKET_TRADE_STATUS");
    }

    @Test
    void getOrderabilityBlocksWhenRealtimeTradeStatusIsHalted() {
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
        realtimeCache.putTrade(new KisRealtimeTradeTick(
                "005930",
                "093000",
                new BigDecimal("81500"),
                new BigDecimal("0.00"),
                new BigDecimal("81600"),
                new BigDecimal("81400"),
                1200L,
                16_200_000L,
                LocalDate.of(2025, 6, 4),
                "0",
                "0",
                "HALT"));

        when(repository.findByCode("005930")).thenReturn(Optional.of(samsungElectronics()));

        Orderability orderability = service.getOrderability("005930", "SELL", 1);

        assertThat(orderability.orderable()).isFalse();
        assertThat(orderability.orderBlockedReason()).isEqualTo("TRADING_HALTED");
        assertThat(orderability.tradingHalted()).isTrue();
        assertThat(orderability.source()).isEqualTo("ORDERABILITY_KIS_WEBSOCKET_TRADE+KIS_WEBSOCKET_TRADE_STATUS");
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

    private StockSummary foreignLimitRestrictedStock() {
        return new StockSummary(
                "015760",
                "한국전력",
                "KEPCO",
                "KOSPI",
                "KR7015760002",
                "00159193");
    }

    private KisCurrentPriceSnapshot kisSnapshotWithForeignOwnership() {
        return kisSnapshotWithForeignOwnership("005930");
    }

    private KisCurrentPriceSnapshot kisSnapshotWithForeignOwnership(String stockCode) {
        return new KisCurrentPriceSnapshot(
                stockCode,
                "삼성전자",
                new BigDecimal("81200"),
                new BigDecimal("1.87"),
                15_500_000L,
                3_642_091_300L,
                new BigDecimal("61.008777"),
                6_718_486_073L,
                new BigDecimal("54.21"));
    }

    private ForeignOwnershipSnapshot foreignOwnershipSnapshot() {
        return foreignOwnershipSnapshot("005930");
    }

    private ForeignOwnershipSnapshot foreignOwnershipSnapshot(String stockCode) {
        return new ForeignOwnershipSnapshot(
                stockCode,
                3_400_000_000L,
                new BigDecimal("51.01"),
                6_720_000_000L,
                new BigDecimal("50.99"),
                LocalDate.of(2025, 6, 2));
    }

    private ForeignOwnershipDailySnapshot foreignOwnershipDailySnapshot(LocalDate baseDate, String exhaustionRate) {
        return foreignOwnershipDailySnapshot("005930", baseDate, exhaustionRate);
    }

    private ForeignOwnershipDailySnapshot foreignOwnershipDailySnapshot(
            String stockCode,
            LocalDate baseDate,
            String exhaustionRate) {
        return new ForeignOwnershipDailySnapshot(
                stockCode,
                baseDate,
                995L,
                new BigDecimal("49.75"),
                1_000L,
                new BigDecimal(exhaustionRate),
                "KRX_DATA_MARKETPLACE_FOREIGN_OWNERSHIP",
                FIXED_CLOCK.instant());
    }

    private MarketDailyPrice dailyPrice(String stockCode, LocalDate tradeDate, String closePrice) {
        return new MarketDailyPrice(
                stockCode,
                tradeDate,
                "KOSPI",
                new BigDecimal("79000"),
                new BigDecimal("82000"),
                new BigDecimal("78500"),
                new BigDecimal(closePrice),
                BigDecimal.ZERO,
                12_000_000L,
                new BigDecimal("960000000000"),
                new BigDecimal(closePrice),
                "KIS_DAILY_ITEM_CHART_PRICE",
                FIXED_CLOCK.instant());
    }

    private MarketDataService marketDataService(
            RealtimeMarketDataCache realtimeMarketDataCache,
            MarketIndexSnapshotRepository marketIndexSnapshotRepository) {
        return marketDataService(realtimeMarketDataCache, marketIndexSnapshotRepository, null);
    }

    private MarketDataService marketDataService(
            RealtimeMarketDataCache realtimeMarketDataCache,
            MarketIndexSnapshotRepository marketIndexSnapshotRepository,
            MarketIndexHistoryService marketIndexHistoryService) {
        return marketDataService(
                realtimeMarketDataCache,
                marketIndexSnapshotRepository,
                marketIndexHistoryService,
                null);
    }

    private MarketDataService marketDataService(
            RealtimeMarketDataCache realtimeMarketDataCache,
            MarketIndexSnapshotRepository marketIndexSnapshotRepository,
            MarketIndexHistoryService marketIndexHistoryService,
            KisIndexCurrentPriceClient kisIndexCurrentPriceClient) {
        return new MarketDataService(
                null,
                null,
                null,
                kisIndexCurrentPriceClient,
                null,
                new InMemoryForeignOwnershipSnapshotCache(),
                new InMemoryForeignOwnershipDailySnapshotRepository(),
                new InMemoryForeignOwnershipPredictionCache(),
                new InMemoryExchangeRateCache(),
                realtimeMarketDataCache,
                marketIndexSnapshotRepository,
                marketIndexHistoryService,
                null,
                new ForeignOwnershipPredictionEngine(FIXED_CLOCK),
                FIXED_CLOCK);
    }

    private KisIndexCurrentPriceSnapshot indexCurrentSnapshot() {
        return indexCurrentSnapshot("0001", "KOSPI", "KOSPI", "2891.12");
    }

    private KisIndexCurrentPriceSnapshot indexCurrentSnapshot(
            String indexCode,
            String indexName,
            String market,
            String currentValue) {
        return new KisIndexCurrentPriceSnapshot(
                indexCode,
                indexName,
                market,
                new BigDecimal(currentValue),
                "5",
                new BigDecimal("-655.32"),
                new BigDecimal("-7.89"),
                922_000L,
                12_340_000_000L,
                new BigDecimal("8300.00"),
                new BigDecimal("8400.00"),
                new BigDecimal(currentValue),
                Instant.now(FIXED_CLOCK),
                "KIS_INDEX_CURRENT_PRICE");
    }

    private MarketIndexQuote indexQuote(String indexCode, String indexName, String source) {
        return indexQuote(indexCode, indexName, "KOSPI", "2800.12", source);
    }

    private MarketIndexQuote indexQuote(
            String indexCode,
            String indexName,
            String market,
            String currentValue,
            String source) {
        return indexQuote(indexCode, indexName, market, currentValue, Instant.now(FIXED_CLOCK), source);
    }

    private MarketIndexQuote indexQuote(
            String indexCode,
            String indexName,
            String market,
            String currentValue,
            Instant marketDataTime,
            String source) {
        return new MarketIndexQuote(
                indexCode,
                indexName,
                market,
                new BigDecimal(currentValue),
                "2",
                new BigDecimal("1.23"),
                new BigDecimal("0.44"),
                120_000L,
                5_000_000_000L,
                new BigDecimal("2790.00"),
                new BigDecimal("2810.00"),
                new BigDecimal("2780.00"),
                marketDataTime,
                source);
    }

    private MarketIndexIntradayPrice indexIntraday(LocalDateTime bucketStart, String closeValue) {
        return indexIntraday("0001", "KOSPI", "KOSPI", bucketStart, closeValue);
    }

    private MarketIndexIntradayPrice indexIntraday(
            String indexCode,
            String indexName,
            String market,
            LocalDateTime bucketStart,
            String closeValue) {
        return new MarketIndexIntradayPrice(
                indexCode,
                indexName,
                market,
                bucketStart,
                new BigDecimal("2880.00"),
                new BigDecimal("2895.00"),
                new BigDecimal("2875.00"),
                new BigDecimal(closeValue),
                1_200_000L,
                new BigDecimal("3400000000000"),
                "KIS_TIME_INDEX_CHART_PRICE",
                FIXED_CLOCK.instant());
    }

    private KisRealtimeIndexTick indexTick(String indexCode, String indexName, String source) {
        return indexTick(indexCode, indexName, "090000", FIXED_CLOCK.instant(), source);
    }

    private KisRealtimeIndexTick indexTick(
            String indexCode,
            String indexName,
            String tradeTime,
            Instant marketDataTime,
            String source) {
        return indexTick(indexCode, indexName, tradeTime, marketDataTime, source, "2801.50");
    }

    private KisRealtimeIndexTick indexTick(
            String indexCode,
            String indexName,
            String tradeTime,
            Instant marketDataTime,
            String source,
            String currentValue) {
        return new KisRealtimeIndexTick(
                indexCode,
                indexName,
                "KOSPI",
                tradeTime,
                new BigDecimal(currentValue),
                "2",
                new BigDecimal("2.61"),
                new BigDecimal("0.09"),
                122_000L,
                5_100_000_000L,
                new BigDecimal("2790.00"),
                new BigDecimal("2812.00"),
                new BigDecimal("2780.00"),
                new BigDecimal("1.50"),
                marketDataTime,
                source);
    }
}

package com.hana.omnilens.market.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.hana.omnilens.market.domain.MarketQuote;
import com.hana.omnilens.market.domain.OrderBook;
import com.hana.omnilens.market.domain.StockSummary;
import com.hana.omnilens.provider.market.KisCurrentPriceClient;
import com.hana.omnilens.provider.market.KisCurrentPriceSnapshot;
import com.hana.omnilens.provider.market.KisRealtimeOrderBookSnapshot;
import com.hana.omnilens.provider.market.KisRealtimeTradeTick;
import com.hana.omnilens.provider.market.KrxForeignOwnershipSnapshot;
import com.hana.omnilens.provider.market.PublicDataStockPriceSnapshot;
import com.hana.omnilens.provider.market.PublicDataStockSecuritiesClient;

@Service
public class MarketDataService {

    private static final StockSummary DEFAULT_STOCK = new StockSummary(
            "005930",
            "삼성전자",
            "Samsung Electronics",
            "KOSPI",
            "KR7005930003",
            "00126380");
    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    private final PublicDataStockSecuritiesClient publicDataStockSecuritiesClient;
    private final KisCurrentPriceClient kisCurrentPriceClient;
    private final StockMasterRepository stockMasterRepository;
    private final ForeignOwnershipSnapshotCache foreignOwnershipSnapshotCache;
    private final ExchangeRateCache exchangeRateCache;
    private final RealtimeMarketDataCache realtimeMarketDataCache;
    private final Clock clock;

    @Autowired
    public MarketDataService(
            PublicDataStockSecuritiesClient publicDataStockSecuritiesClient,
            KisCurrentPriceClient kisCurrentPriceClient,
            StockMasterRepository stockMasterRepository,
            ForeignOwnershipSnapshotCache foreignOwnershipSnapshotCache,
            ExchangeRateCache exchangeRateCache,
            RealtimeMarketDataCache realtimeMarketDataCache) {
        this(
                publicDataStockSecuritiesClient,
                kisCurrentPriceClient,
                stockMasterRepository,
                foreignOwnershipSnapshotCache,
                exchangeRateCache,
                realtimeMarketDataCache,
                Clock.system(KOREA_ZONE));
    }

    MarketDataService(
            PublicDataStockSecuritiesClient publicDataStockSecuritiesClient,
            KisCurrentPriceClient kisCurrentPriceClient,
            StockMasterRepository stockMasterRepository,
            ForeignOwnershipSnapshotCache foreignOwnershipSnapshotCache,
            ExchangeRateCache exchangeRateCache,
            RealtimeMarketDataCache realtimeMarketDataCache,
            Clock clock) {
        this.publicDataStockSecuritiesClient = publicDataStockSecuritiesClient;
        this.kisCurrentPriceClient = kisCurrentPriceClient;
        this.stockMasterRepository = stockMasterRepository;
        this.foreignOwnershipSnapshotCache = foreignOwnershipSnapshotCache;
        this.exchangeRateCache = exchangeRateCache;
        this.realtimeMarketDataCache = realtimeMarketDataCache;
        this.clock = clock;
    }

    public MarketQuote getQuote(String stockCode, String localCurrency, BigDecimal fxRate) {
        PriceLookup priceLookup = latestPriceSnapshot(stockCode);
        StockSummary stock = stockMasterRepository.findByCode(stockCode).orElse(DEFAULT_STOCK);
        ForeignOwnershipLookup foreignOwnership = latestForeignOwnershipSnapshot(stock);

        BigDecimal currentPrice = priceLookup.currentPriceKrw()
                .orElse(new BigDecimal("78500"));
        BigDecimal effectiveFxRate = resolveFxRate(localCurrency, fxRate);
        BigDecimal localPrice = currentPrice.multiply(effectiveFxRate).setScale(4, RoundingMode.HALF_UP);

        return new MarketQuote(
                stockCode,
                priceLookup.stockName().orElse(stock.stockName()),
                stock.stockNameEn(),
                priceLookup.market().orElse(stock.market()),
                currentPrice,
                priceLookup.changeRate().orElse(new BigDecimal("1.42")),
                priceLookup.volume().orElse(12193000L),
                currentPrice,
                "KRW",
                localPrice,
                localCurrency,
                foreignOwnership.snapshot().map(KrxForeignOwnershipSnapshot::foreignOwnedQuantity).orElse(3642091300L),
                foreignOwnership.snapshot().map(KrxForeignOwnershipSnapshot::foreignOwnershipRate)
                        .orElse(new BigDecimal("54.19")),
                foreignOwnership.snapshot().map(KrxForeignOwnershipSnapshot::foreignLimitExhaustionRate)
                        .orElse(new BigDecimal("54.19")),
                foreignOwnership.snapshot().map(KrxForeignOwnershipSnapshot::baseDate)
                        .orElse(priceLookup.baseDate()
                                .orElse(LocalDate.now(clock).minusDays(1))),
                Instant.now(clock),
                source(priceLookup.source(), foreignOwnership.source()))
        ;
    }

    public OrderBook getOrderBook(String stockCode) {
        Optional<KisRealtimeOrderBookSnapshot> realtimeOrderBook =
                realtimeMarketDataCache.latestOrderBook(stockCode);
        if (realtimeOrderBook.isPresent()) {
            KisRealtimeOrderBookSnapshot snapshot = realtimeOrderBook.orElseThrow();
            return new OrderBook(
                    stockCode,
                    snapshot.asks().stream()
                            .map(level -> new OrderBook.OrderBookLevel(level.priceKrw(), level.quantity()))
                            .toList(),
                    snapshot.bids().stream()
                            .map(level -> new OrderBook.OrderBookLevel(level.priceKrw(), level.quantity()))
                            .toList(),
                    Instant.now(clock),
                    "KIS_WEBSOCKET_ORDERBOOK");
        }
        return new OrderBook(
                stockCode,
                List.of(
                        new OrderBook.OrderBookLevel(new BigDecimal("78600"), 1200L),
                        new OrderBook.OrderBookLevel(new BigDecimal("78700"), 2100L)),
                List.of(
                        new OrderBook.OrderBookLevel(new BigDecimal("78500"), 1800L),
                        new OrderBook.OrderBookLevel(new BigDecimal("78400"), 2600L)),
                Instant.now(),
                "MOCK_KIS_WEBSOCKET");
    }

    public List<StockSummary> searchStocks(String query) {
        return stockMasterRepository.search(query);
    }

    public StockSummary getStock(String stockCode) {
        return stockMasterRepository.findByCode(stockCode)
                .orElseThrow(() -> new StockMasterNotFoundException(stockCode));
    }

    public ExchangeRateSnapshot updateExchangeRate(String localCurrency, BigDecimal fxRate) {
        return exchangeRateCache.put(localCurrency, fxRate, Instant.now(clock));
    }

    private BigDecimal resolveFxRate(String localCurrency, BigDecimal requestFxRate) {
        if (requestFxRate != null) {
            return requestFxRate;
        }
        return exchangeRateCache.find(localCurrency)
                .map(ExchangeRateSnapshot::fxRate)
                .orElse(BigDecimal.ONE);
    }

    private PriceLookup latestPriceSnapshot(String stockCode) {
        Optional<KisRealtimeTradeTick> realtimeTrade = realtimeMarketDataCache.latestTrade(stockCode);
        if (realtimeTrade.isPresent()) {
            return PriceLookup.realtime(realtimeTrade.orElseThrow());
        }
        try {
            Optional<KisCurrentPriceSnapshot> kisSnapshot = kisCurrentPriceClient.findCurrentPrice(stockCode);
            if (kisSnapshot.isPresent()) {
                return PriceLookup.kis(kisSnapshot.orElseThrow(), LocalDate.now(clock));
            }
        } catch (RuntimeException exception) {
            // KIS 인증 또는 일시 장애가 있어도 공공데이터 snapshot으로 시세 응답을 유지한다.
        }
        return latestPublicDataSnapshot(stockCode)
                .map(PriceLookup::publicData)
                .orElseGet(PriceLookup::empty);
    }

    private Optional<PublicDataStockPriceSnapshot> latestPublicDataSnapshot(String stockCode) {
        LocalDate baseDate = LocalDate.now(clock).minusDays(1);
        for (int daysBack = 0; daysBack < 7; daysBack++) {
            try {
                Optional<PublicDataStockPriceSnapshot> snapshot =
                        publicDataStockSecuritiesClient.findPrice(stockCode, baseDate.minusDays(daysBack));
                if (snapshot.isPresent()) {
                    return snapshot;
                }
            } catch (RuntimeException exception) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private ForeignOwnershipLookup latestForeignOwnershipSnapshot(StockSummary stock) {
        return foreignOwnershipSnapshotCache.find(stock.stockCode())
                .map(ForeignOwnershipLookup::cache)
                .orElseGet(ForeignOwnershipLookup::empty);
    }

    private String source(PriceSource priceSource, ForeignOwnershipSource foreignOwnershipSource) {
        if (priceSource == PriceSource.KIS_WEBSOCKET_TRADE && foreignOwnershipSource == ForeignOwnershipSource.CACHE) {
            return "KIS_WEBSOCKET_TRADE+KRX_FOREIGN_OWNERSHIP_CACHE";
        }
        if (priceSource == PriceSource.KIS_WEBSOCKET_TRADE) {
            return "KIS_WEBSOCKET_TRADE";
        }
        if (priceSource == PriceSource.KIS_OPEN_API && foreignOwnershipSource == ForeignOwnershipSource.CACHE) {
            return "KIS_OPEN_API+KRX_FOREIGN_OWNERSHIP_CACHE";
        }
        if (priceSource == PriceSource.KIS_OPEN_API) {
            return "KIS_OPEN_API";
        }
        if (priceSource == PriceSource.PUBLIC_DATA && foreignOwnershipSource == ForeignOwnershipSource.CACHE) {
            return "PUBLIC_DATA_STOCK_SECURITIES+KRX_FOREIGN_OWNERSHIP_CACHE";
        }
        if (priceSource == PriceSource.PUBLIC_DATA) {
            return "PUBLIC_DATA_STOCK_SECURITIES";
        }
        if (foreignOwnershipSource == ForeignOwnershipSource.CACHE) {
            return "MOCK_MARKET_DATA+KRX_FOREIGN_OWNERSHIP_CACHE";
        }
        return "MOCK_MARKET_DATA";
    }

    private record PriceLookup(
            Optional<String> stockName,
            Optional<String> market,
            Optional<BigDecimal> currentPriceKrw,
            Optional<BigDecimal> changeRate,
            Optional<Long> volume,
            Optional<LocalDate> baseDate,
            PriceSource source
    ) {
        private static PriceLookup realtime(KisRealtimeTradeTick tick) {
            return new PriceLookup(
                    Optional.empty(),
                    Optional.empty(),
                    Optional.of(tick.currentPriceKrw()),
                    Optional.of(tick.changeRate()),
                    Optional.of(tick.accumulatedVolume()),
                    Optional.of(tick.businessDate()),
                    PriceSource.KIS_WEBSOCKET_TRADE);
        }

        private static PriceLookup kis(KisCurrentPriceSnapshot snapshot, LocalDate baseDate) {
            return new PriceLookup(
                    Optional.ofNullable(snapshot.stockName()).filter(name -> !name.isBlank()),
                    Optional.empty(),
                    Optional.of(snapshot.currentPriceKrw()),
                    Optional.of(snapshot.changeRate()),
                    Optional.of(snapshot.volume()),
                    Optional.of(baseDate),
                    PriceSource.KIS_OPEN_API);
        }

        private static PriceLookup publicData(PublicDataStockPriceSnapshot snapshot) {
            return new PriceLookup(
                    Optional.of(snapshot.stockName()),
                    Optional.of(snapshot.market()),
                    Optional.of(snapshot.closingPriceKrw()),
                    Optional.of(snapshot.changeRate()),
                    Optional.of(snapshot.volume()),
                    Optional.of(snapshot.baseDate()),
                    PriceSource.PUBLIC_DATA);
        }

        private static PriceLookup empty() {
            return new PriceLookup(
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    PriceSource.NONE);
        }
    }

    private enum PriceSource {
        KIS_WEBSOCKET_TRADE,
        KIS_OPEN_API,
        PUBLIC_DATA,
        NONE
    }

    private record ForeignOwnershipLookup(
            Optional<KrxForeignOwnershipSnapshot> snapshot,
            ForeignOwnershipSource source
    ) {
        private static ForeignOwnershipLookup cache(KrxForeignOwnershipSnapshot snapshot) {
            return new ForeignOwnershipLookup(Optional.of(snapshot), ForeignOwnershipSource.CACHE);
        }

        private static ForeignOwnershipLookup empty() {
            return new ForeignOwnershipLookup(Optional.empty(), ForeignOwnershipSource.NONE);
        }
    }

    private enum ForeignOwnershipSource {
        CACHE,
        NONE
    }
}

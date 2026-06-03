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
import com.hana.omnilens.provider.market.KrxForeignOwnershipClient;
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
    private final KrxForeignOwnershipClient krxForeignOwnershipClient;
    private final StockMasterRepository stockMasterRepository;
    private final ForeignOwnershipSnapshotCache foreignOwnershipSnapshotCache;
    private final Clock clock;

    @Autowired
    public MarketDataService(
            PublicDataStockSecuritiesClient publicDataStockSecuritiesClient,
            KrxForeignOwnershipClient krxForeignOwnershipClient,
            StockMasterRepository stockMasterRepository,
            ForeignOwnershipSnapshotCache foreignOwnershipSnapshotCache) {
        this(
                publicDataStockSecuritiesClient,
                krxForeignOwnershipClient,
                stockMasterRepository,
                foreignOwnershipSnapshotCache,
                Clock.system(KOREA_ZONE));
    }

    MarketDataService(
            PublicDataStockSecuritiesClient publicDataStockSecuritiesClient,
            KrxForeignOwnershipClient krxForeignOwnershipClient,
            StockMasterRepository stockMasterRepository,
            ForeignOwnershipSnapshotCache foreignOwnershipSnapshotCache,
            Clock clock) {
        this.publicDataStockSecuritiesClient = publicDataStockSecuritiesClient;
        this.krxForeignOwnershipClient = krxForeignOwnershipClient;
        this.stockMasterRepository = stockMasterRepository;
        this.foreignOwnershipSnapshotCache = foreignOwnershipSnapshotCache;
        this.clock = clock;
    }

    public MarketQuote getQuote(String stockCode, String localCurrency, BigDecimal fxRate) {
        Optional<PublicDataStockPriceSnapshot> snapshot = latestPublicDataSnapshot(stockCode);
        StockSummary stock = stockMasterRepository.findByCode(stockCode).orElse(DEFAULT_STOCK);
        ForeignOwnershipLookup foreignOwnership = latestForeignOwnershipSnapshot(stock);

        BigDecimal currentPrice = snapshot
                .map(PublicDataStockPriceSnapshot::closingPriceKrw)
                .orElse(new BigDecimal("78500"));
        BigDecimal effectiveFxRate = fxRate == null ? BigDecimal.ONE : fxRate;
        BigDecimal localPrice = currentPrice.multiply(effectiveFxRate).setScale(4, RoundingMode.HALF_UP);

        return new MarketQuote(
                stockCode,
                snapshot.map(PublicDataStockPriceSnapshot::stockName).orElse(stock.stockName()),
                stock.stockNameEn(),
                snapshot.map(PublicDataStockPriceSnapshot::market).orElse(stock.market()),
                currentPrice,
                snapshot.map(PublicDataStockPriceSnapshot::changeRate).orElse(new BigDecimal("1.42")),
                snapshot.map(PublicDataStockPriceSnapshot::volume).orElse(12193000L),
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
                        .orElse(snapshot.map(PublicDataStockPriceSnapshot::baseDate)
                                .orElse(LocalDate.now(clock).minusDays(1))),
                Instant.now(clock),
                source(snapshot.isPresent(), foreignOwnership.source()))
        ;
    }

    public OrderBook getOrderBook(String stockCode) {
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
        LocalDate baseDate = LocalDate.now(clock).minusDays(1);
        for (int daysBack = 0; daysBack < 7; daysBack++) {
            try {
                Optional<KrxForeignOwnershipSnapshot> snapshot = krxForeignOwnershipClient.findForeignOwnership(
                        stock.stockCode(),
                        stock.stockName(),
                        stock.isinCode(),
                        baseDate.minusDays(daysBack));
                if (snapshot.isPresent()) {
                    foreignOwnershipSnapshotCache.put(snapshot.orElseThrow());
                    return ForeignOwnershipLookup.live(snapshot);
                }
            } catch (RuntimeException exception) {
                continue;
            }
        }
        return foreignOwnershipSnapshotCache.find(stock.stockCode())
                .map(ForeignOwnershipLookup::cache)
                .orElseGet(ForeignOwnershipLookup::empty);
    }

    private String source(boolean priceFromProvider, ForeignOwnershipSource foreignOwnershipSource) {
        if (priceFromProvider && foreignOwnershipSource == ForeignOwnershipSource.LIVE_PROVIDER) {
            return "PUBLIC_DATA_STOCK_SECURITIES+KRX_FOREIGN_OWNERSHIP";
        }
        if (priceFromProvider && foreignOwnershipSource == ForeignOwnershipSource.CACHE) {
            return "PUBLIC_DATA_STOCK_SECURITIES+KRX_FOREIGN_OWNERSHIP_CACHE";
        }
        if (priceFromProvider) {
            return "PUBLIC_DATA_STOCK_SECURITIES";
        }
        if (foreignOwnershipSource == ForeignOwnershipSource.LIVE_PROVIDER) {
            return "MOCK_MARKET_DATA+KRX_FOREIGN_OWNERSHIP";
        }
        if (foreignOwnershipSource == ForeignOwnershipSource.CACHE) {
            return "MOCK_MARKET_DATA+KRX_FOREIGN_OWNERSHIP_CACHE";
        }
        return "MOCK_MARKET_DATA";
    }

    private record ForeignOwnershipLookup(
            Optional<KrxForeignOwnershipSnapshot> snapshot,
            ForeignOwnershipSource source
    ) {
        private static ForeignOwnershipLookup live(Optional<KrxForeignOwnershipSnapshot> snapshot) {
            return new ForeignOwnershipLookup(snapshot, ForeignOwnershipSource.LIVE_PROVIDER);
        }

        private static ForeignOwnershipLookup cache(KrxForeignOwnershipSnapshot snapshot) {
            return new ForeignOwnershipLookup(Optional.of(snapshot), ForeignOwnershipSource.CACHE);
        }

        private static ForeignOwnershipLookup empty() {
            return new ForeignOwnershipLookup(Optional.empty(), ForeignOwnershipSource.NONE);
        }
    }

    private enum ForeignOwnershipSource {
        LIVE_PROVIDER,
        CACHE,
        NONE
    }
}

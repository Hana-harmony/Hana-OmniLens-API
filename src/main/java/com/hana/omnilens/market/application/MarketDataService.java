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
import com.hana.omnilens.provider.market.PublicDataStockPriceSnapshot;
import com.hana.omnilens.provider.market.PublicDataStockSecuritiesClient;

@Service
public class MarketDataService {

    private static final StockSummary DEFAULT_STOCK = new StockSummary(
            "005930",
            "삼성전자",
            "Samsung Electronics",
            "KOSPI");
    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    private final PublicDataStockSecuritiesClient publicDataStockSecuritiesClient;
    private final StockMasterRepository stockMasterRepository;
    private final Clock clock;

    @Autowired
    public MarketDataService(
            PublicDataStockSecuritiesClient publicDataStockSecuritiesClient,
            StockMasterRepository stockMasterRepository) {
        this(publicDataStockSecuritiesClient, stockMasterRepository, Clock.system(KOREA_ZONE));
    }

    MarketDataService(
            PublicDataStockSecuritiesClient publicDataStockSecuritiesClient,
            StockMasterRepository stockMasterRepository,
            Clock clock) {
        this.publicDataStockSecuritiesClient = publicDataStockSecuritiesClient;
        this.stockMasterRepository = stockMasterRepository;
        this.clock = clock;
    }

    public MarketQuote getQuote(String stockCode, String localCurrency, BigDecimal fxRate) {
        Optional<PublicDataStockPriceSnapshot> snapshot = latestPublicDataSnapshot(stockCode);
        StockSummary stock = stockMasterRepository.findByCode(stockCode).orElse(DEFAULT_STOCK);

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
                3642091300L,
                new BigDecimal("54.19"),
                new BigDecimal("54.19"),
                snapshot.map(PublicDataStockPriceSnapshot::baseDate).orElse(LocalDate.now(clock).minusDays(1)),
                Instant.now(clock),
                snapshot.isPresent() ? "PUBLIC_DATA_STOCK_SECURITIES" : "MOCK_MARKET_DATA")
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
}

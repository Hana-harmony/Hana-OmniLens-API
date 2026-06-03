package com.hana.omnilens.market.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;

import com.hana.omnilens.market.domain.MarketQuote;
import com.hana.omnilens.market.domain.OrderBook;
import com.hana.omnilens.market.domain.StockSummary;

@Service
public class MarketDataService {

    public MarketQuote getQuote(String stockCode, String localCurrency, BigDecimal fxRate) {
        BigDecimal currentPrice = new BigDecimal("78500");
        BigDecimal effectiveFxRate = fxRate == null ? BigDecimal.ONE : fxRate;
        BigDecimal localPrice = currentPrice.multiply(effectiveFxRate).setScale(4, RoundingMode.HALF_UP);

        return new MarketQuote(
                stockCode,
                "삼성전자",
                "Samsung Electronics",
                "KOSPI",
                currentPrice,
                new BigDecimal("1.42"),
                12193000L,
                new BigDecimal("78400"),
                "KRW",
                localPrice,
                localCurrency,
                3642091300L,
                new BigDecimal("54.19"),
                new BigDecimal("54.19"),
                LocalDate.now().minusDays(1),
                Instant.now(),
                "MOCK_KIS_KRX_EXIMBANK")
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
        return List.of(
                new StockSummary("005930", "삼성전자", "Samsung Electronics", "KOSPI"),
                new StockSummary("000660", "SK하이닉스", "SK hynix", "KOSPI"))
                .stream()
                .filter(stock -> stock.stockCode().contains(query)
                        || stock.stockName().contains(query)
                        || stock.stockNameEn().toLowerCase().contains(query.toLowerCase()))
                .toList();
    }
}

package com.hana.omnilens.market.api;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hana.omnilens.market.application.MarketDataService;
import com.hana.omnilens.market.domain.MarketQuote;
import com.hana.omnilens.market.domain.OrderBook;
import com.hana.omnilens.market.domain.StockSummary;

@Validated
@RestController
@RequestMapping("/api/v1/market")
public class MarketDataController {

    private final MarketDataService marketDataService;

    public MarketDataController(MarketDataService marketDataService) {
        this.marketDataService = marketDataService;
    }

    @GetMapping("/stocks/{stockCode}/quote")
    public MarketQuote getQuote(
            @PathVariable @Pattern(regexp = "\\d{6}") String stockCode,
            @RequestParam(defaultValue = "USD") @Pattern(regexp = "[A-Z]{3}") String currency,
            @RequestParam(required = false) @DecimalMin("0.000001") BigDecimal fxRate) {
        return marketDataService.getQuote(stockCode, currency, fxRate);
    }

    @GetMapping("/stocks/{stockCode}/orderbook")
    public OrderBook getOrderBook(@PathVariable @Pattern(regexp = "\\d{6}") String stockCode) {
        return marketDataService.getOrderBook(stockCode);
    }

    @GetMapping("/stocks/search")
    public List<StockSummary> searchStocks(@RequestParam @Size(min = 1, max = 40) String query) {
        return marketDataService.searchStocks(query);
    }
}

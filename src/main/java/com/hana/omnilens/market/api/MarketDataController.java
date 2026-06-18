package com.hana.omnilens.market.api;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hana.omnilens.common.api.ApiResponse;
import com.hana.omnilens.market.application.MarketDataService;
import com.hana.omnilens.market.domain.MarketQuote;
import com.hana.omnilens.market.domain.OrderBook;
import com.hana.omnilens.market.domain.StockSummary;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Validated
@RestController
@RequestMapping("/api/v1/market")
@Tag(name = "Market", description = "Korean stock quote, order book, and stock search APIs")
public class MarketDataController {

    private final MarketDataService marketDataService;

    public MarketDataController(MarketDataService marketDataService) {
        this.marketDataService = marketDataService;
    }

    @GetMapping("/stocks/{stockCode}")
    public StockSummary getStock(@PathVariable @Pattern(regexp = "\\d{6}") String stockCode) {
        return marketDataService.getStock(stockCode);
    }

    @GetMapping("/stocks/{stockCode}/quote")
    @Operation(summary = "Get Korean stock quote with KRW and requested local currency price")
    public ApiResponse<MarketQuote> getQuote(
            @PathVariable @Pattern(regexp = "\\d{6}") String stockCode,
            @RequestParam(defaultValue = "USD") @Pattern(regexp = "[A-Z]{3}") String currency,
            @RequestParam(required = false) @DecimalMin("0.000001") BigDecimal fxRate) {
        return ApiResponse.success(marketDataService.getQuote(stockCode, currency, fxRate));
    }

    @GetMapping("/quotes")
    @Operation(summary = "Get all or requested Korean stock quotes with KRW and local currency prices")
    public ApiResponse<List<MarketQuote>> getQuotes(
            @RequestParam(required = false) @Size(max = 200) List<@Pattern(regexp = "\\d{6}") String> stockCodes,
            @RequestParam(required = false) @Pattern(regexp = "KOSPI|KOSDAQ|KONEX") String market,
            @RequestParam(defaultValue = "USD") @Pattern(regexp = "[A-Z]{3}") String currency,
            @RequestParam(required = false) @DecimalMin("0.000001") BigDecimal fxRate,
            @RequestParam(defaultValue = "500") @Min(1) @Max(2000) int limit) {
        return ApiResponse.success(marketDataService.getQuotes(stockCodes, market, currency, fxRate, limit));
    }

    @GetMapping("/stocks/{stockCode}/orderbook")
    @Operation(summary = "Get Korean stock order book")
    public ApiResponse<OrderBook> getOrderBook(@PathVariable @Pattern(regexp = "\\d{6}") String stockCode) {
        return ApiResponse.success(marketDataService.getOrderBook(stockCode));
    }

    @GetMapping("/stocks/search")
    @Operation(summary = "Search Korean stocks")
    public ApiResponse<List<StockSummary>> searchStocks(@RequestParam @Size(min = 1, max = 40) String query) {
        return ApiResponse.success(marketDataService.searchStocks(query));
    }

    @PutMapping("/exchange-rates/{currency}")
    public ExchangeRateResponse updateExchangeRate(
            @PathVariable @Pattern(regexp = "[A-Z]{3}") String currency,
            @Valid @RequestBody ExchangeRateUpdateRequest request) {
        return ExchangeRateResponse.from(marketDataService.updateExchangeRate(currency, request.fxRate()));
    }
}

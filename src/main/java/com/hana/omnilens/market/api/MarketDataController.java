package com.hana.omnilens.market.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hana.omnilens.common.api.ApiResponse;
import com.hana.omnilens.market.application.ForeignOwnershipBackfillResult;
import com.hana.omnilens.market.application.ForeignOwnershipCollectionResult;
import com.hana.omnilens.market.application.ForeignOwnershipModelTrainingService;
import com.hana.omnilens.market.application.ForeignOwnershipPredictionPrecomputeResult;
import com.hana.omnilens.market.application.ForeignOwnershipPredictionPrecomputeService;
import com.hana.omnilens.market.application.ForeignOwnershipRefreshService;
import com.hana.omnilens.market.application.GlobalPeerMatchService;
import com.hana.omnilens.market.application.MarketDataService;
import com.hana.omnilens.market.application.MarketChartWarmupResult;
import com.hana.omnilens.market.application.MarketHistoryService;
import com.hana.omnilens.market.domain.GlobalPeerMatchResponse;
import com.hana.omnilens.market.domain.MarketDailyPrice;
import com.hana.omnilens.market.domain.MarketIndexQuote;
import com.hana.omnilens.market.domain.MarketIntradayPrice;
import com.hana.omnilens.market.domain.MarketQuote;
import com.hana.omnilens.market.domain.Orderability;
import com.hana.omnilens.market.domain.OrderBook;
import com.hana.omnilens.market.domain.StockDetail;
import com.hana.omnilens.market.domain.StockSummary;
import com.hana.omnilens.provider.market.KisRealtimeSubscriptionRequestResult;
import com.hana.omnilens.provider.market.OnDemandKisRealtimeSubscriptionService;
import com.hana.omnilens.provider.ai.HannahAiForeignOwnershipRetrainResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Validated
@RestController
@RequestMapping("/api/v1/market")
@Tag(name = "Market", description = "Korean stock quote, order book, and stock search APIs")
public class MarketDataController {

    private final MarketDataService marketDataService;
    private final MarketHistoryService marketHistoryService;
    private final ForeignOwnershipRefreshService foreignOwnershipRefreshService;
    private final ForeignOwnershipModelTrainingService foreignOwnershipModelTrainingService;
    private final ForeignOwnershipPredictionPrecomputeService foreignOwnershipPredictionPrecomputeService;
    private final OnDemandKisRealtimeSubscriptionService onDemandKisRealtimeSubscriptionService;
    private final GlobalPeerMatchService globalPeerMatchService;

    public MarketDataController(
            MarketDataService marketDataService,
            MarketHistoryService marketHistoryService,
            ForeignOwnershipRefreshService foreignOwnershipRefreshService,
            ForeignOwnershipModelTrainingService foreignOwnershipModelTrainingService,
            ForeignOwnershipPredictionPrecomputeService foreignOwnershipPredictionPrecomputeService,
            OnDemandKisRealtimeSubscriptionService onDemandKisRealtimeSubscriptionService,
            GlobalPeerMatchService globalPeerMatchService) {
        this.marketDataService = marketDataService;
        this.marketHistoryService = marketHistoryService;
        this.foreignOwnershipRefreshService = foreignOwnershipRefreshService;
        this.foreignOwnershipModelTrainingService = foreignOwnershipModelTrainingService;
        this.foreignOwnershipPredictionPrecomputeService = foreignOwnershipPredictionPrecomputeService;
        this.onDemandKisRealtimeSubscriptionService = onDemandKisRealtimeSubscriptionService;
        this.globalPeerMatchService = globalPeerMatchService;
    }

    @GetMapping("/stocks/{stockCode}")
    public ApiResponse<StockSummary> getStock(@PathVariable @Pattern(regexp = "\\d{6}") String stockCode) {
        return ApiResponse.success(marketDataService.getStock(stockCode));
    }

    @GetMapping("/stocks/{stockCode}/quote")
    @Operation(summary = "국내 주식 현재가와 요청 통화 환산가 조회")
    public ApiResponse<MarketQuote> getQuote(
            @PathVariable @Pattern(regexp = "\\d{6}") String stockCode,
            @RequestParam(defaultValue = "USD") @Pattern(regexp = "[A-Z]{3}") String currency,
            @RequestParam(required = false) @DecimalMin("0.000001") BigDecimal fxRate) {
        return ApiResponse.success(marketDataService.getQuote(stockCode, currency, fxRate));
    }

    @GetMapping("/stocks/{stockCode}/detail")
    @Operation(summary = "거래소 앱 화면용 국내 주식 상세 조회")
    public ApiResponse<StockDetail> getStockDetail(
            @PathVariable @Pattern(regexp = "\\d{6}") String stockCode,
            @RequestParam(defaultValue = "USD") @Pattern(regexp = "[A-Z]{3}") String currency,
            @RequestParam(required = false) @DecimalMin("0.000001") BigDecimal fxRate) {
        return ApiResponse.success(marketDataService.getStockDetail(stockCode, currency, fxRate));
    }

    @GetMapping("/stocks/{stockCode}/global-peers")
    @Operation(summary = "국내 주식 글로벌 피어 매칭 조회")
    public ApiResponse<GlobalPeerMatchResponse> getGlobalPeers(
            @PathVariable @Pattern(regexp = "\\d{6}") String stockCode) {
        return ApiResponse.success(globalPeerMatchService.match(stockCode));
    }

    @GetMapping("/quotes")
    @Operation(summary = "전체 또는 요청 국내 주식 현재가 목록 조회")
    public ApiResponse<List<MarketQuote>> getQuotes(
            @RequestParam(required = false) @Size(max = 200) List<@Pattern(regexp = "\\d{6}") String> stockCodes,
            @RequestParam(required = false) @Pattern(regexp = "KOSPI|KOSDAQ|KONEX") String market,
            @RequestParam(defaultValue = "USD") @Pattern(regexp = "[A-Z]{3}") String currency,
            @RequestParam(required = false) @DecimalMin("0.000001") BigDecimal fxRate,
            @RequestParam(defaultValue = "500") @Min(1) @Max(2000) int limit) {
        return ApiResponse.success(marketDataService.getQuotes(stockCodes, market, currency, fxRate, limit));
    }

    @GetMapping("/indices")
    @Operation(summary = "국내 시장 지수 실시간 snapshot 조회")
    public ApiResponse<List<MarketIndexQuote>> getIndices() {
        return ApiResponse.success(marketDataService.getIndices());
    }

    @GetMapping("/stocks/{stockCode}/orderbook")
    @Operation(summary = "국내 주식 호가 조회")
    public ApiResponse<OrderBook> getOrderBook(@PathVariable @Pattern(regexp = "\\d{6}") String stockCode) {
        return ApiResponse.success(marketDataService.getOrderBook(stockCode));
    }

    @GetMapping("/stocks/{stockCode}/orderability")
    @Operation(summary = "협력사 모의 주문 전 외국인 한도, VI, 가격제한 상태 조회")
    public ApiResponse<Orderability> getOrderability(
            @PathVariable @Pattern(regexp = "\\d{6}") String stockCode,
            @RequestParam @Pattern(regexp = "BUY|SELL") String side,
            @RequestParam @Min(1) long quantity) {
        return ApiResponse.success(marketDataService.getOrderability(stockCode, side, quantity));
    }

    @GetMapping("/stocks/{stockCode}/history")
    @Operation(summary = "KRX 기반 국내 주식 일봉 OHLCV 조회")
    public ApiResponse<List<MarketDailyPrice>> getHistory(
            @PathVariable @Pattern(regexp = "\\d{6}") String stockCode,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(defaultValue = "365") @Min(1) @Max(5000) int limit) {
        return ApiResponse.success(marketHistoryService.getHistory(stockCode, from, to, limit));
    }

    @GetMapping("/stocks/{stockCode}/intraday")
    @Operation(summary = "KIS 기반 국내 주식 분봉 OHLCV 조회")
    public ApiResponse<List<MarketIntradayPrice>> getIntradayHistory(
            @PathVariable @Pattern(regexp = "\\d{6}") String stockCode,
            @RequestParam(required = false) LocalDate date,
            @RequestParam(defaultValue = "390") @Min(1) @Max(600) int limit,
            @RequestParam(defaultValue = "true") boolean fetchMissing) {
        return ApiResponse.success(marketHistoryService.getIntradayHistory(stockCode, date, limit, fetchMissing));
    }

    @PostMapping("/stocks/{stockCode}/realtime-subscription")
    @Operation(summary = "국내 주식 KIS 실시간 source 구독 요청")
    public ApiResponse<KisRealtimeSubscriptionRequestResult> subscribeRealtimeSource(
            @PathVariable @Pattern(regexp = "\\d{6}") String stockCode,
            @RequestParam(defaultValue = "REGULAR") @Pattern(regexp = "REGULAR|AFTER_HOURS_REAL") String session) {
        if ("AFTER_HOURS_REAL".equals(session)) {
            return ApiResponse.success(onDemandKisRealtimeSubscriptionService.subscribeRealAfterHours(stockCode));
        }
        return ApiResponse.success(onDemandKisRealtimeSubscriptionService.subscribeRegular(stockCode));
    }

    @DeleteMapping("/stocks/{stockCode}/realtime-subscription")
    @Operation(summary = "국내 주식 KIS 실시간 source 구독 해제")
    public ApiResponse<KisRealtimeSubscriptionRequestResult> unsubscribeRealtimeSource(
            @PathVariable @Pattern(regexp = "\\d{6}") String stockCode,
            @RequestParam(defaultValue = "REGULAR") @Pattern(regexp = "REGULAR|AFTER_HOURS_REAL") String session) {
        if ("AFTER_HOURS_REAL".equals(session)) {
            return ApiResponse.success(onDemandKisRealtimeSubscriptionService.unsubscribeRealAfterHours(stockCode));
        }
        return ApiResponse.success(onDemandKisRealtimeSubscriptionService.unsubscribeRegular(stockCode));
    }

    @PostMapping("/history/collect")
    @Operation(summary = "기준일 국내 주식 일봉 OHLCV 전체 수집")
    public ApiResponse<MarketHistoryCollectionResponse> collectHistory(
            @RequestParam(required = false) LocalDate baseDate) {
        return ApiResponse.success(MarketHistoryCollectionResponse.from(
                marketHistoryService.collectDailyHistory(baseDate)));
    }

    @PostMapping("/chart/warmup")
    @Operation(summary = "모바일 차트용 1D 분봉 및 1W/1M 일봉 cache warmup")
    public ApiResponse<MarketChartWarmupResult> warmupChart(
            @RequestParam(required = false) LocalDate baseDate) {
        return ApiResponse.success(marketHistoryService.warmupChartHistory(baseDate));
    }

    @PostMapping("/stocks/{stockCode}/foreign-ownership/refresh")
    @Operation(summary = "국내 주식 KRX 외국인 보유 snapshot cache 갱신")
    public ApiResponse<ForeignOwnershipRefreshResponse> refreshForeignOwnership(
            @PathVariable @Pattern(regexp = "\\d{6}") String stockCode,
            @RequestParam(required = false) LocalDate baseDate) {
        return ApiResponse.success(ForeignOwnershipRefreshResponse.from(
                foreignOwnershipRefreshService.refresh(stockCode, baseDate)));
    }

    @PostMapping("/foreign-ownership/collect")
    @Operation(summary = "전체 또는 요청 국내 주식 KRX 외국인 보유 일별 snapshot 수집")
    public ApiResponse<ForeignOwnershipCollectionResponse> collectForeignOwnership(
            @RequestParam(required = false) LocalDate baseDate,
            @RequestParam(required = false) @Size(max = 500) List<@Pattern(regexp = "\\d{6}") String> stockCodes,
            @RequestParam(defaultValue = "5000") @Min(1) @Max(5000) int limit,
            @RequestParam(defaultValue = "0") @Min(0) @Max(60000) long requestDelayMs) {
        ForeignOwnershipCollectionResult result =
                foreignOwnershipRefreshService.collect(baseDate, stockCodes, limit, requestDelayMs);
        return ApiResponse.success(ForeignOwnershipCollectionResponse.from(result));
    }

    @PostMapping("/foreign-ownership/backfill")
    @Operation(summary = "누락 평일 외국인 보유 일별 snapshot 백필")
    public ApiResponse<ForeignOwnershipBackfillResponse> backfillForeignOwnership(
            @RequestParam(required = false) LocalDate fromDate,
            @RequestParam(required = false) LocalDate toDate,
            @RequestParam(required = false) @Size(max = 500) List<@Pattern(regexp = "\\d{6}") String> stockCodes,
            @RequestParam(defaultValue = "5000") @Min(1) @Max(5000) int limit,
            @RequestParam(defaultValue = "0") @Min(0) @Max(60000) long requestDelayMs) {
        ForeignOwnershipBackfillResult result = foreignOwnershipRefreshService.backfillMissing(
                fromDate,
                toDate,
                stockCodes,
                limit,
                requestDelayMs);
        return ApiResponse.success(ForeignOwnershipBackfillResponse.from(result));
    }

    @PostMapping("/foreign-ownership/model/retrain")
    @Operation(summary = "외국인 취득한도 제한 종목 history export 및 Hannah ML 재학습")
    public ApiResponse<HannahAiForeignOwnershipRetrainResponse> retrainForeignOwnershipModel() {
        return ApiResponse.success(foreignOwnershipModelTrainingService.retrainRestrictedUniverse());
    }

    @PostMapping("/foreign-ownership/predictions/precompute")
    @Operation(summary = "외국인 취득한도 제한 종목 금일 예측 선계산 및 cache 저장")
    public ApiResponse<ForeignOwnershipPredictionPrecomputeResult> precomputeForeignOwnershipPredictions() {
        return ApiResponse.success(foreignOwnershipPredictionPrecomputeService.precomputeRestrictedUniverse());
    }

    @GetMapping("/stocks/search")
    @Operation(summary = "국내 주식 검색")
    public ApiResponse<List<StockSummary>> searchStocks(@RequestParam @Size(min = 1, max = 40) String query) {
        return ApiResponse.success(marketDataService.searchStocks(query));
    }

    @PutMapping("/exchange-rates/{currency}")
    public ApiResponse<ExchangeRateResponse> updateExchangeRate(
            @PathVariable @Pattern(regexp = "[A-Z]{3}") String currency,
            @Valid @RequestBody ExchangeRateUpdateRequest request) {
        return ApiResponse.success(ExchangeRateResponse.from(marketDataService.updateExchangeRate(currency, request.fxRate())));
    }
}

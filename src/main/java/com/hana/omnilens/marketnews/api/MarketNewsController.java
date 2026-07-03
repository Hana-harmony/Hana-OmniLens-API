package com.hana.omnilens.marketnews.api;

import java.time.Duration;
import java.time.Instant;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.hana.omnilens.common.api.ApiResponse;
import com.hana.omnilens.marketnews.application.MarketNewsCollectionResult;
import com.hana.omnilens.marketnews.application.MarketNewsCollectionService;
import com.hana.omnilens.marketnews.application.MarketNewsEventRepository;
import com.hana.omnilens.marketnews.domain.MarketNewsEvent;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Validated
@RestController
@RequestMapping("/api/v1/market/news")
@Tag(name = "Market News", description = "Korean market-wide news APIs")
public class MarketNewsController {

    private final MarketNewsEventRepository marketNewsEventRepository;
    private final MarketNewsCollectionService marketNewsCollectionService;

    public MarketNewsController(
            MarketNewsEventRepository marketNewsEventRepository,
            MarketNewsCollectionService marketNewsCollectionService) {
        this.marketNewsEventRepository = marketNewsEventRepository;
        this.marketNewsCollectionService = marketNewsCollectionService;
    }

    @GetMapping
    @Operation(summary = "List Korean market-wide news")
    public ApiResponse<MarketNewsListResponse> listMarketNews(
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        var news = marketNewsEventRepository.findLatest(limit);
        return ApiResponse.success(new MarketNewsListResponse(news.size(), news));
    }

    @GetMapping("/trending")
    @Operation(summary = "List Korean market-wide news ranked by recent detail views")
    public ApiResponse<MarketNewsListResponse> listTrendingMarketNews(
            @RequestParam(defaultValue = "24") @Min(1) @Max(720) int windowHours,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit) {
        var news = marketNewsEventRepository.findTrending(
                Instant.now().minus(Duration.ofHours(windowHours)),
                limit);
        return ApiResponse.success(new MarketNewsListResponse(news.size(), news));
    }

    @GetMapping("/{newsId}")
    @Operation(summary = "Get Korean market-wide news detail")
    public ApiResponse<MarketNewsEvent> getMarketNews(
            @PathVariable @Size(min = 1, max = 80) String newsId) {
        var event = marketNewsEventRepository.findByNewsId(newsId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "market news not found"));
        marketNewsEventRepository.recordView(newsId, Instant.now());
        return ApiResponse.success(event);
    }

    @PostMapping("/collect")
    @Operation(summary = "Collect Korean market-wide news now")
    public ApiResponse<MarketNewsCollectionResult> collectMarketNews(
            @Valid @RequestBody MarketNewsCollectRequest request) {
        return ApiResponse.success(marketNewsCollectionService.collect(request.queries(), request.display()));
    }

    @PostMapping("/reprocess")
    @Operation(summary = "Reprocess stored Korean market-wide news summaries and translations")
    public ApiResponse<MarketNewsListResponse> reprocessMarketNews(
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        var news = marketNewsCollectionService.reprocessLatest(limit);
        return ApiResponse.success(new MarketNewsListResponse(news.size(), news));
    }

    @PostMapping("/reprocess/quality-issues")
    @Operation(summary = "Reprocess stored Korean market-wide news with broken summaries or translations")
    public ApiResponse<MarketNewsListResponse> reprocessMarketNewsQualityIssues(
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        var news = marketNewsCollectionService.reprocessSummaryQualityIssues(limit);
        return ApiResponse.success(new MarketNewsListResponse(news.size(), news));
    }
}

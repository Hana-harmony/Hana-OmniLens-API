package com.hana.omniconnect.marketnews.api;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hana.omniconnect.common.api.ApiResponse;
import com.hana.omniconnect.common.api.KeysetCursor;
import com.hana.omniconnect.common.exception.BusinessException;
import com.hana.omniconnect.common.exception.ErrorCode;
import com.hana.omniconnect.marketnews.application.MarketNewsCollectionResult;
import com.hana.omniconnect.marketnews.application.MarketNewsCollectionService;
import com.hana.omniconnect.marketnews.application.MarketNewsEventRepository;
import com.hana.omniconnect.marketnews.domain.MarketNewsEvent;

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
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
            @RequestParam(required = false) @Size(max = 512) String cursor) {
        KeysetCursor decodedCursor = decodeCursor(cursor);
        var candidates = marketNewsEventRepository.findLatestBefore(decodedCursor, candidateLimit(limit + 1));
        var displayable = displayableNews(candidates, limit + 1);
        boolean hasNext = displayable.size() > limit;
        var news = List.copyOf(displayable.subList(0, Math.min(limit, displayable.size())));
        String nextCursor = hasNext && !news.isEmpty() ? cursorOf(news.get(news.size() - 1)) : null;
        return ApiResponse.success(new MarketNewsListResponse(news.size(), news, nextCursor));
    }

    @GetMapping("/trending")
    @Operation(summary = "List Korean market-wide news ranked by recent detail views")
    public ApiResponse<MarketNewsListResponse> listTrendingMarketNews(
            @RequestParam(defaultValue = "24") @Min(1) @Max(720) int windowHours,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit) {
        var news = displayableNews(marketNewsEventRepository.findTrending(
                Instant.now().minus(Duration.ofHours(windowHours)),
                candidateLimit(limit)), limit);
        return ApiResponse.success(new MarketNewsListResponse(news.size(), news));
    }

    @GetMapping("/{newsId}")
    @Operation(summary = "Get Korean market-wide news detail")
    public ApiResponse<MarketNewsEvent> getMarketNews(
            @PathVariable @Size(min = 1, max = 80) String newsId) {
        var event = marketNewsCollectionService.ensureDisplayableFullArticleByNewsId(newsId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "market news not found"));
        if (!marketNewsCollectionService.isDisplayableNews(event)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "market news analysis not available");
        }
        marketNewsEventRepository.recordView(newsId, Instant.now());
        return ApiResponse.success(sanitizeAvailability(event));
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

    @PostMapping("/{newsId}/reprocess")
    @Operation(summary = "Reprocess one stored Korean market-wide news item")
    public ApiResponse<MarketNewsEvent> reprocessMarketNews(
            @PathVariable @Size(min = 1, max = 80) String newsId) {
        var event = marketNewsCollectionService.reprocessByNewsId(newsId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "market news not found"));
        return ApiResponse.success(event);
    }

    @PostMapping("/reprocess/quality-issues")
    @Operation(summary = "Reprocess stored Korean market-wide news with broken summaries or translations")
    public ApiResponse<MarketNewsListResponse> reprocessMarketNewsQualityIssues(
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        var news = marketNewsCollectionService.reprocessSummaryQualityIssues(limit);
        return ApiResponse.success(new MarketNewsListResponse(news.size(), news));
    }

    private List<MarketNewsEvent> sanitizeAvailability(List<MarketNewsEvent> events) {
        return events.stream().map(this::sanitizeAvailability).toList();
    }

    private List<MarketNewsEvent> displayableNews(List<MarketNewsEvent> events, int limit) {
        return sanitizeAvailability(events).stream()
                .filter(marketNewsCollectionService::isDisplayableNews)
                .limit(limit)
                .toList();
    }

    private int candidateLimit(int limit) {
        return Math.max(limit, Math.min(100, limit * 5));
    }

    private KeysetCursor decodeCursor(String cursor) {
        if (!org.springframework.util.StringUtils.hasText(cursor)) {
            return null;
        }
        try {
            return KeysetCursor.decode(cursor);
        } catch (IllegalArgumentException exception) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "invalid cursor",
                    exception);
        }
    }

    private String cursorOf(MarketNewsEvent event) {
        return KeysetCursor.encode(event.publishedAt(), event.createdAt(), event.newsId());
    }

    private MarketNewsEvent sanitizeAvailability(MarketNewsEvent event) {
        String availability = displayContentAvailability(event.originalContent(), event.translatedContent());
        if (availability.equals(event.contentAvailability())) {
            return event;
        }
        return new MarketNewsEvent(
                event.newsId(),
                event.query(),
                event.title(),
                event.translatedTitle(),
                event.summary(),
                event.summaryLines(),
                event.translatedSummary(),
                event.originalContent(),
                event.translatedContent(),
                event.imageUrls(),
                availability,
                event.originalUrl(),
                event.canonicalUrl(),
                event.sourceLicensePolicy(),
                event.glossaryTerms(),
                event.sentiment(),
                event.importance(),
                event.translationProvider(),
                event.translationModelVersion(),
                event.translationStatus(),
                event.duplicateKey(),
                event.publishedAt(),
                event.createdAt());
    }

    private String displayContentAvailability(String originalContent, String translatedContent) {
        if (originalContent == null || originalContent.isBlank()) {
            return "DISCOVERY_ONLY";
        }
        if (!hasCompleteArticleBody(originalContent)) {
            return "SUMMARY_ONLY";
        }
        return translatedContent == null || translatedContent.isBlank() ? "ORIGINAL_TEXT_ONLY" : "FULL_TEXT";
    }

    private boolean hasCompleteArticleBody(String originalContent) {
        String normalized = originalContent.replaceAll("\\s+", " ").trim();
        return normalized.length() >= 260
                && sourceSentenceCount(normalized) >= 3
                && !Pattern.compile("(?:\\.\\.\\.|…)[\\s\"'”’)]*$").matcher(normalized).find();
    }

    private int sourceSentenceCount(String text) {
        return (int) Pattern.compile("[.!?。]|(?:다|요|니다|습니다|한다|했다|됐다|된다)(?=\\s|$)")
                .splitAsStream(text)
                .filter(value -> value != null && !value.isBlank())
                .count();
    }
}

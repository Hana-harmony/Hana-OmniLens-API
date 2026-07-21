package com.hana.omniconnect.alert.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Optional;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hana.omniconnect.alert.application.AlertEventRepository;
import com.hana.omniconnect.alert.application.AlertAnalysisPublishingService;
import com.hana.omniconnect.alert.application.AlertProviderCollectionService;
import com.hana.omniconnect.alert.application.AlertStreamingService;
import com.hana.omniconnect.alert.application.PartnerWatchlistService;
import com.hana.omniconnect.alert.domain.AlertEvent;
import com.hana.omniconnect.common.api.ApiResponse;
import com.hana.omniconnect.common.api.KeysetCursor;
import com.hana.omniconnect.common.exception.BusinessException;
import com.hana.omniconnect.common.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.hana.omniconnect.security.PartnerAuthorizationService;

@Validated
@RestController
@RequestMapping("/api/v1/alerts")
@Tag(name = "Alerts", description = "News and disclosure intelligence event APIs")
public class AlertController {

    private static final int MAX_CLIENT_LIMIT = 100;

    private final AlertStreamingService alertStreamingService;
    private final AlertAnalysisPublishingService alertAnalysisPublishingService;
    private final AlertProviderCollectionService alertProviderCollectionService;
    private final PartnerWatchlistService partnerWatchlistService;
    private final PartnerAuthorizationService partnerAuthorizationService;
    private final AlertEventRepository alertEventRepository;

    public AlertController(
            AlertStreamingService alertStreamingService,
            AlertAnalysisPublishingService alertAnalysisPublishingService,
            AlertProviderCollectionService alertProviderCollectionService,
            PartnerWatchlistService partnerWatchlistService,
            PartnerAuthorizationService partnerAuthorizationService,
            AlertEventRepository alertEventRepository) {
        this.alertStreamingService = alertStreamingService;
        this.alertAnalysisPublishingService = alertAnalysisPublishingService;
        this.alertProviderCollectionService = alertProviderCollectionService;
        this.partnerWatchlistService = partnerWatchlistService;
        this.partnerAuthorizationService = partnerAuthorizationService;
        this.alertEventRepository = alertEventRepository;
    }

    @GetMapping("/watchlists/{partnerId}")
    public ApiResponse<PartnerWatchlistResponse> getPartnerWatchlist(
            @PathVariable @Size(min = 1, max = 80) @Pattern(regexp = "[A-Za-z0-9._:-]+") String partnerId) {
        partnerAuthorizationService.assertPartnerAccess(partnerId);
        return ApiResponse.success(partnerWatchlistService.get(partnerId));
    }

    @PutMapping("/watchlists/{partnerId}")
    public ApiResponse<PartnerWatchlistResponse> replacePartnerWatchlist(
            @PathVariable @Size(min = 1, max = 80) @Pattern(regexp = "[A-Za-z0-9._:-]+") String partnerId,
            @Valid @RequestBody PartnerWatchlistUpdateRequest request) {
        partnerAuthorizationService.assertPartnerAccess(partnerId);
        return ApiResponse.success(partnerWatchlistService.replace(partnerId, request.stockCodes()));
    }

    @PostMapping("/events")
    @Operation(summary = "Publish analyzed news or disclosure event to partner streams")
    public ApiResponse<AlertEvent> publish(@Valid @RequestBody AlertPublishRequest request) {
        partnerAuthorizationService.assertPartnerAccess(request.partnerId());
        return ApiResponse.success(alertStreamingService.publish(request));
    }

    @GetMapping("/events/{alertId}")
    @Operation(summary = "Get stored news or disclosure event detail")
    public ApiResponse<AlertEvent> getEvent(@PathVariable @Size(min = 1, max = 80) String alertId) {
        AlertEvent event = alertEventRepository.findByAlertId(alertId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "alert event not found"));
        if (!alertAnalysisPublishingService.isDisplayableFullArticle(event)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "alert event not available");
        }
        return ApiResponse.success(event);
    }

    @PostMapping("/events/{alertId}/reprocess")
    @Operation(summary = "Reprocess stored news or disclosure event summary and translation")
    public ApiResponse<AlertEvent> reprocessEvent(@PathVariable @Size(min = 1, max = 80) String alertId) {
        AlertEvent event = alertEventRepository.findByAlertId(alertId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "alert event not found"));
        partnerAuthorizationService.assertPartnerAccess(event.partnerId());
        return ApiResponse.success(alertAnalysisPublishingService.reprocess(event));
    }

    @GetMapping("/stocks/{stockCode}/events")
    @Operation(summary = "List stored news and disclosure events for a stock")
    public ApiResponse<AlertEventListResponse> listStockEvents(
            @PathVariable @Pattern(regexp = "\\d{6}") String stockCode,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) @Size(max = 512) String cursor) {
        int effectiveLimit = Math.max(1, Math.min(limit, MAX_CLIENT_LIMIT));
        KeysetCursor decodedCursor = decodeCursor(cursor);
        int candidateLimit = Math.min(1_000, Math.max(effectiveLimit + 1, (effectiveLimit + 1) * 10));
        List<AlertEvent> candidates = alertEventRepository.findByStockCodeBefore(
                stockCode,
                decodedCursor,
                candidateLimit).stream()
                .filter(alertAnalysisPublishingService::isDisplayableFullArticle)
                .limit(effectiveLimit + 1L)
                .toList();
        boolean hasNext = candidates.size() > effectiveLimit;
        List<AlertEvent> events = List.copyOf(candidates.subList(0, Math.min(effectiveLimit, candidates.size())));
        String nextCursor = hasNext && !events.isEmpty() ? cursorOf(events.get(events.size() - 1)) : null;
        return ApiResponse.success(new AlertEventListResponse(stockCode, events, nextCursor));
    }

    @PostMapping("/stocks/{stockCode}/events/reprocess")
    @Operation(summary = "Reprocess stored news and disclosure events for a stock")
    public ApiResponse<AlertEventListResponse> reprocessStockEvents(
            @PathVariable @Pattern(regexp = "\\d{6}") String stockCode,
            @RequestParam(defaultValue = "20") int limit) {
        int effectiveLimit = Math.max(1, Math.min(limit, 100));
        var events = alertEventRepository.findByStockCode(stockCode, effectiveLimit).stream()
                .peek(event -> partnerAuthorizationService.assertPartnerAccess(event.partnerId()))
                .map(alertAnalysisPublishingService::reprocess)
                .toList();
        return ApiResponse.success(new AlertEventListResponse(stockCode, events));
    }

    @PostMapping("/events/reprocess/quality-issues")
    @Operation(summary = "Reprocess stored news and disclosure events with broken summaries or translations")
    public ApiResponse<AlertEventReprocessResponse> reprocessQualityIssueEvents(
            @RequestParam(defaultValue = "20") int limit) {
        int effectiveLimit = Math.max(1, Math.min(limit, 100));
        var events = alertEventRepository.findSummaryQualityIssues(effectiveLimit).stream()
                .peek(event -> partnerAuthorizationService.assertPartnerAccess(event.partnerId()))
                .map(alertAnalysisPublishingService::reprocessIfPossible)
                .flatMap(Optional::stream)
                .toList();
        return ApiResponse.success(new AlertEventReprocessResponse(events.size(), events));
    }

    @PostMapping("/analyze-and-publish")
    public ApiResponse<AlertEvent> analyzeAndPublish(@Valid @RequestBody AlertAnalysisPublishRequest request) {
        partnerAuthorizationService.assertPartnerAccess(request.partnerId());
        return ApiResponse.success(alertAnalysisPublishingService.analyzeAndPublish(request));
    }

    @PostMapping("/collect-and-publish")
    public ApiResponse<AlertCollectPublishResponse> collectAndPublish(
            @Valid @RequestBody AlertCollectPublishRequest request) {
        partnerAuthorizationService.assertPartnerAccess(request.partnerId());
        return ApiResponse.success(alertProviderCollectionService.collectAnalyzeAndPublish(request));
    }

    private KeysetCursor decodeCursor(String cursor) {
        if (!org.springframework.util.StringUtils.hasText(cursor)) {
            return null;
        }
        try {
            return KeysetCursor.decode(cursor);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "invalid cursor");
        }
    }

    private String cursorOf(AlertEvent event) {
        return KeysetCursor.encode(event.publishedAt(), event.createdAt(), event.alertId());
    }
}

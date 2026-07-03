package com.hana.omnilens.alert.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.validation.annotation.Validated;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.hana.omnilens.alert.application.AlertEventRepository;
import com.hana.omnilens.alert.application.AlertAnalysisPublishingService;
import com.hana.omnilens.alert.application.AlertProviderCollectionService;
import com.hana.omnilens.alert.application.AlertStreamingService;
import com.hana.omnilens.alert.application.PartnerWatchlistService;
import com.hana.omnilens.alert.domain.AlertEvent;
import com.hana.omnilens.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.hana.omnilens.security.PartnerAuthorizationService;

@Validated
@RestController
@RequestMapping("/api/v1/alerts")
@Tag(name = "Alerts", description = "News and disclosure intelligence event APIs")
public class AlertController {

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
        return ApiResponse.success(alertEventRepository.findByAlertId(alertId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "alert event not found")));
    }

    @PostMapping("/events/{alertId}/reprocess")
    @Operation(summary = "Reprocess stored news or disclosure event summary and translation")
    public ApiResponse<AlertEvent> reprocessEvent(@PathVariable @Size(min = 1, max = 80) String alertId) {
        AlertEvent event = alertEventRepository.findByAlertId(alertId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "alert event not found"));
        partnerAuthorizationService.assertPartnerAccess(event.partnerId());
        return ApiResponse.success(alertAnalysisPublishingService.reprocess(event));
    }

    @GetMapping("/stocks/{stockCode}/events")
    @Operation(summary = "List stored news and disclosure events for a stock")
    public ApiResponse<AlertEventListResponse> listStockEvents(
            @PathVariable @Pattern(regexp = "\\d{6}") String stockCode,
            @RequestParam(defaultValue = "20") int limit) {
        int effectiveLimit = Math.max(1, Math.min(limit, 100));
        return ApiResponse.success(new AlertEventListResponse(
                stockCode,
                alertEventRepository.findByStockCode(stockCode, effectiveLimit)));
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
}

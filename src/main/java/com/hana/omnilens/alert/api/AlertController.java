package com.hana.omnilens.alert.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hana.omnilens.alert.application.AlertAnalysisPublishingService;
import com.hana.omnilens.alert.application.AlertProviderCollectionService;
import com.hana.omnilens.alert.application.AlertStreamingService;
import com.hana.omnilens.alert.application.PartnerWatchlistService;
import com.hana.omnilens.alert.domain.AlertEvent;

@Validated
@RestController
@RequestMapping("/api/v1/alerts")
public class AlertController {

    private final AlertStreamingService alertStreamingService;
    private final AlertAnalysisPublishingService alertAnalysisPublishingService;
    private final AlertProviderCollectionService alertProviderCollectionService;
    private final PartnerWatchlistService partnerWatchlistService;

    public AlertController(
            AlertStreamingService alertStreamingService,
            AlertAnalysisPublishingService alertAnalysisPublishingService,
            AlertProviderCollectionService alertProviderCollectionService,
            PartnerWatchlistService partnerWatchlistService) {
        this.alertStreamingService = alertStreamingService;
        this.alertAnalysisPublishingService = alertAnalysisPublishingService;
        this.alertProviderCollectionService = alertProviderCollectionService;
        this.partnerWatchlistService = partnerWatchlistService;
    }

    @GetMapping("/watchlists/{partnerId}")
    public PartnerWatchlistResponse getPartnerWatchlist(
            @PathVariable @Size(min = 1, max = 80) @Pattern(regexp = "[A-Za-z0-9._:-]+") String partnerId) {
        return partnerWatchlistService.get(partnerId);
    }

    @PutMapping("/watchlists/{partnerId}")
    public PartnerWatchlistResponse replacePartnerWatchlist(
            @PathVariable @Size(min = 1, max = 80) @Pattern(regexp = "[A-Za-z0-9._:-]+") String partnerId,
            @Valid @RequestBody PartnerWatchlistUpdateRequest request) {
        return partnerWatchlistService.replace(partnerId, request.stockCodes());
    }

    @PostMapping("/events")
    public AlertEvent publish(@Valid @RequestBody AlertPublishRequest request) {
        return alertStreamingService.publish(request);
    }

    @PostMapping("/analyze-and-publish")
    public AlertEvent analyzeAndPublish(@Valid @RequestBody AlertAnalysisPublishRequest request) {
        return alertAnalysisPublishingService.analyzeAndPublish(request);
    }

    @PostMapping("/collect-and-publish")
    public AlertCollectPublishResponse collectAndPublish(@Valid @RequestBody AlertCollectPublishRequest request) {
        return alertProviderCollectionService.collectAnalyzeAndPublish(request);
    }
}

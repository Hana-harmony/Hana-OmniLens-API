package com.hana.omnilens.alert.api;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hana.omnilens.alert.application.AlertAnalysisPublishingService;
import com.hana.omnilens.alert.application.AlertProviderCollectionService;
import com.hana.omnilens.alert.application.AlertStreamingService;
import com.hana.omnilens.alert.domain.AlertEvent;

@RestController
@RequestMapping("/api/v1/alerts")
public class AlertController {

    private final AlertStreamingService alertStreamingService;
    private final AlertAnalysisPublishingService alertAnalysisPublishingService;
    private final AlertProviderCollectionService alertProviderCollectionService;

    public AlertController(
            AlertStreamingService alertStreamingService,
            AlertAnalysisPublishingService alertAnalysisPublishingService,
            AlertProviderCollectionService alertProviderCollectionService) {
        this.alertStreamingService = alertStreamingService;
        this.alertAnalysisPublishingService = alertAnalysisPublishingService;
        this.alertProviderCollectionService = alertProviderCollectionService;
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

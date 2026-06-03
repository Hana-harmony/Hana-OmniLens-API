package com.hana.omnilens.alert.api;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hana.omnilens.alert.application.AlertStreamingService;
import com.hana.omnilens.alert.domain.AlertEvent;

@RestController
@RequestMapping("/api/v1/alerts")
public class AlertController {

    private final AlertStreamingService alertStreamingService;

    public AlertController(AlertStreamingService alertStreamingService) {
        this.alertStreamingService = alertStreamingService;
    }

    @PostMapping("/events")
    public AlertEvent publish(@Valid @RequestBody AlertPublishRequest request) {
        return alertStreamingService.publish(request);
    }
}

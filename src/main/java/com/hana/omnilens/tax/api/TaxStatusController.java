package com.hana.omnilens.tax.api;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hana.omnilens.common.api.ApiResponse;
import com.hana.omnilens.tax.application.TaxStatusSyncService;
import com.hana.omnilens.tax.application.TaxTreatyCaseClassificationService;
import com.hana.omnilens.tax.domain.TaxStatusSyncRequest;
import com.hana.omnilens.tax.domain.TaxStatusSyncResponse;
import com.hana.omnilens.tax.domain.TaxTreatyCaseClassificationRequest;
import com.hana.omnilens.tax.domain.TaxTreatyCaseClassificationResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/tax")
@Tag(name = "Tax", description = "Tax refund status synchronization APIs")
public class TaxStatusController {

    private final TaxStatusSyncService taxStatusSyncService;
    private final TaxTreatyCaseClassificationService taxTreatyCaseClassificationService;

    public TaxStatusController(
            TaxStatusSyncService taxStatusSyncService,
            TaxTreatyCaseClassificationService taxTreatyCaseClassificationService) {
        this.taxStatusSyncService = taxStatusSyncService;
        this.taxTreatyCaseClassificationService = taxTreatyCaseClassificationService;
    }

    @PostMapping("/refund-cases/sync")
    @Operation(summary = "Synchronize a partner tax refund case status")
    public ApiResponse<TaxStatusSyncResponse> syncRefundCase(@Valid @RequestBody TaxStatusSyncRequest request) {
        return ApiResponse.success(taxStatusSyncService.sync(request));
    }

    @PostMapping("/refund-cases/classify")
    @Operation(summary = "Classify a Korea-Hong Kong tax treaty refund case")
    public ApiResponse<TaxTreatyCaseClassificationResponse> classifyRefundCase(
            @Valid @RequestBody TaxTreatyCaseClassificationRequest request) {
        return ApiResponse.success(taxTreatyCaseClassificationService.classify(request));
    }
}

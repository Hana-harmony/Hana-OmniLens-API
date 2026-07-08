package com.hana.omnilens.tax.api;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hana.omnilens.common.api.ApiResponse;
import com.hana.omnilens.tax.application.TaxDocumentVerificationService;
import com.hana.omnilens.tax.domain.TaxDocumentVerificationRequest;
import com.hana.omnilens.tax.domain.TaxDocumentVerificationResponse;

import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/tax/documents")
@Tag(name = "Tax", description = "Tax document OCR verification APIs")
public class TaxDocumentVerificationController {

    private final TaxDocumentVerificationService service;

    public TaxDocumentVerificationController(TaxDocumentVerificationService service) {
        this.service = service;
    }

    @PostMapping("/verify")
    public ApiResponse<TaxDocumentVerificationResponse> verify(
            @Valid @RequestBody TaxDocumentVerificationRequest request) {
        return ApiResponse.success(service.verify(request));
    }
}

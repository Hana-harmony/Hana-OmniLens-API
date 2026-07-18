package com.hana.omnilens.security.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hana.omnilens.common.api.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/partner")
@Tag(name = "Security", description = "Partner authentication and credential operation APIs")
public class PartnerReadinessController {

    public static final String CONTRACT_VERSION = "hmac-sha256-v1";

    @GetMapping("/readiness")
    @Operation(summary = "Verify partner API key and HMAC request-signing interoperability")
    public ApiResponse<PartnerReadinessResponse> readiness() {
        return ApiResponse.success(new PartnerReadinessResponse("UP", CONTRACT_VERSION));
    }
}

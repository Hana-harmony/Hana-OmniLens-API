package com.hana.omnilens.security.api;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hana.omnilens.common.api.ApiResponse;
import com.hana.omnilens.security.PartnerAuthorizationService;
import com.hana.omnilens.security.PartnerCredentialRotationResult;
import com.hana.omnilens.security.PartnerCredentialRotationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Validated
@RestController
@RequestMapping("/api/v1/security/partners")
@Tag(name = "Security", description = "Partner credential operation APIs")
public class PartnerCredentialController {

    private final PartnerCredentialRotationService rotationService;
    private final PartnerAuthorizationService authorizationService;

    public PartnerCredentialController(
            PartnerCredentialRotationService rotationService,
            PartnerAuthorizationService authorizationService) {
        this.rotationService = rotationService;
        this.authorizationService = authorizationService;
    }

    @PostMapping("/{partnerId}/credentials/rotate")
    @Operation(summary = "Rotate a partner API key using the bootstrap admin key")
    public ApiResponse<PartnerCredentialRotationResult> rotate(
            @PathVariable @Size(min = 1, max = 80) @Pattern(regexp = "[A-Za-z0-9._:-]+") String partnerId) {
        authorizationService.assertBootstrapAccess();
        return ApiResponse.success(rotationService.rotate(partnerId));
    }
}

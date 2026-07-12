package com.hana.omnilens.portal.api;

import java.util.List;
import java.util.Base64;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import com.hana.omnilens.common.api.ApiResponse;
import com.hana.omnilens.portal.PortalAccessService;
import com.hana.omnilens.portal.PortalAccountService;
import com.hana.omnilens.portal.PortalApiKeyApplication;
import com.hana.omnilens.portal.PortalApiKeyApplicationService;
import com.hana.omnilens.portal.PortalUser;
import com.hana.omnilens.term.application.KoreanFinancialTermExplanationService;
import com.hana.omnilens.term.domain.KoreanFinancialTermClickStat;
import com.hana.omnilens.tax.refund.TaxRefundBackofficeCase;
import com.hana.omnilens.tax.refund.TaxRefundBackofficeService;
import com.hana.omnilens.tax.refund.TaxCorrectionRequestPdfService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/portal")
@Tag(name = "Portal", description = "Hana OmniLens partner portal and administrator APIs")
public class PortalApiKeyController {

    private final PortalAccessService accessService;
    private final PortalAccountService accountService;
    private final PortalApiKeyApplicationService applicationService;
    private final KoreanFinancialTermExplanationService termExplanationService;
    private final TaxRefundBackofficeService taxRefundBackofficeService;
    private final TaxCorrectionRequestPdfService correctionRequestPdfService;

    public PortalApiKeyController(
            PortalAccessService accessService,
            PortalAccountService accountService,
            PortalApiKeyApplicationService applicationService,
            KoreanFinancialTermExplanationService termExplanationService,
            TaxRefundBackofficeService taxRefundBackofficeService,
            TaxCorrectionRequestPdfService correctionRequestPdfService) {
        this.accessService = accessService;
        this.accountService = accountService;
        this.applicationService = applicationService;
        this.termExplanationService = termExplanationService;
        this.taxRefundBackofficeService = taxRefundBackofficeService;
        this.correctionRequestPdfService = correctionRequestPdfService;
    }

    @GetMapping("/me")
    @Operation(summary = "Get the signed-in portal member")
    public ApiResponse<PortalAuthController.PortalUserResponse> me(HttpServletRequest request) {
        return ApiResponse.success(PortalAuthController.PortalUserResponse.from(accessService.requireUser(request)));
    }

    @PostMapping("/me/password")
    @Operation(summary = "Change the signed-in portal member password")
    public ApiResponse<PortalAuthController.PortalSessionResponse> changePassword(
            HttpServletRequest request,
            @Valid @RequestBody PasswordChangeRequest body) {
        return ApiResponse.success(PortalAuthController.PortalSessionResponse.from(accountService.changePassword(
                accessService.requireUser(request), body.currentPassword(), body.newPassword(), body.newPasswordConfirmation())));
    }

    @PostMapping("/api-key-applications")
    @Operation(summary = "Request a permanent Hana OmniLens partner API key")
    public ApiResponse<ApiKeyApplicationResponse> requestApiKey(HttpServletRequest request) {
        return ApiResponse.success(ApiKeyApplicationResponse.from(applicationService.request(accessService.requireUser(request)), null));
    }

    @GetMapping("/api-key-applications")
    @Operation(summary = "List the signed-in member's API key applications")
    public ApiResponse<List<ApiKeyApplicationResponse>> myApplications(HttpServletRequest request) {
        PortalUser user = accessService.requireUser(request);
        return ApiResponse.success(applicationService.listForUser(user).stream()
                .map(application -> ApiKeyApplicationResponse.from(application,
                        application.status().name().equals("APPROVED") ? applicationService.revealKey(application, user) : null))
                .toList());
    }

    @GetMapping("/admin/api-key-applications")
    @Operation(summary = "List API key applications for an administrator")
    public ApiResponse<List<ApiKeyApplicationResponse>> allApplications(HttpServletRequest request) {
        PortalUser administrator = accessService.requireAdmin(request);
        return ApiResponse.success(applicationService.listAll().stream()
                .map(application -> ApiKeyApplicationResponse.from(application, null))
                .toList());
    }

    @PostMapping("/admin/api-key-applications/{applicationId}/approve")
    @Operation(summary = "Approve an API key application and issue a permanent credential")
    public ApiResponse<ApiKeyApplicationResponse> approve(
            HttpServletRequest request,
            @PathVariable @Pattern(regexp = "PAPP-[A-Z0-9]{20}") String applicationId) {
        PortalUser administrator = accessService.requireAdmin(request);
        PortalApiKeyApplication application = applicationService.approve(applicationId, administrator);
        return ApiResponse.success(ApiKeyApplicationResponse.from(application, null));
    }

    @PostMapping("/admin/api-key-applications/{applicationId}/reject")
    @Operation(summary = "Reject an API key application")
    public ApiResponse<ApiKeyApplicationResponse> reject(
            HttpServletRequest request,
            @PathVariable @Pattern(regexp = "PAPP-[A-Z0-9]{20}") String applicationId,
            @Valid @RequestBody RejectionRequest rejection) {
        PortalApiKeyApplication application = applicationService.reject(applicationId, accessService.requireAdmin(request), rejection.reason());
        return ApiResponse.success(ApiKeyApplicationResponse.from(application, null));
    }

    @GetMapping("/admin/term-analytics")
    @Operation(summary = "List glossary explanation click analytics for an administrator")
    public ApiResponse<List<KoreanFinancialTermClickStat>> termAnalytics(HttpServletRequest request) {
        accessService.requireAdmin(request);
        return ApiResponse.success(termExplanationService.stats(100));
    }

    @GetMapping("/admin/tax/refund-cases")
    @Operation(summary = "List synced exchange tax refund cases for an administrator")
    public ApiResponse<List<TaxRefundBackofficeCase>> taxRefundCases(HttpServletRequest request) {
        accessService.requireAdmin(request);
        return ApiResponse.success(taxRefundBackofficeService.list());
    }

    @GetMapping("/admin/tax/refund-cases/{caseId}/correction-fields")
    @Operation(summary = "Load verified document values into a correction-request editor")
    public ApiResponse<Map<String, String>> correctionFields(
            HttpServletRequest request,
            @PathVariable @Pattern(regexp = "TAX-[A-Z0-9]{20}") String caseId) {
        accessService.requireAdmin(request);
        return ApiResponse.success(taxRefundBackofficeService.initialCorrectionFields(caseId));
    }

    @PostMapping(value = "/admin/tax/refund-cases/{caseId}/correction-request.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Render a correction request PDF from verified values and administrator edits")
    public ResponseEntity<byte[]> correctionRequestPdf(
            HttpServletRequest request,
            @PathVariable @Pattern(regexp = "TAX-[A-Z0-9]{20}") String caseId,
            @Valid @RequestBody CorrectionRequestPdfRequest body) {
        PortalUser administrator = accessService.requireAdmin(request);
        taxRefundBackofficeService.caseById(caseId);
        byte[] pdf = correctionRequestPdfService.render(body.fields(), decodeTemplate(body.templateBase64()));
        taxRefundBackofficeService.savePreparedCorrection(caseId, body.fields(), pdf, administrator.userId());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=correction-request-" + caseId + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @PostMapping("/admin/tax/refund-cases/{caseId}/approve")
    @Operation(summary = "Prepare the final correction request and approve the member refund case")
    public ApiResponse<TaxRefundBackofficeCase> approveTaxRefund(
            HttpServletRequest request,
            @PathVariable @Pattern(regexp = "TAX-[A-Z0-9]{20}") String caseId,
            @Valid @RequestBody CorrectionRequestPdfRequest body) {
        PortalUser administrator = accessService.requireAdmin(request);
        byte[] pdf = correctionRequestPdfService.render(body.fields(), decodeTemplate(body.templateBase64()));
        taxRefundBackofficeService.savePreparedCorrection(caseId, body.fields(), pdf, administrator.userId());
        return ApiResponse.success(taxRefundBackofficeService.approve(caseId, administrator.userId()));
    }

    private byte[] decodeTemplate(String templateBase64) {
        if (templateBase64 == null || templateBase64.isBlank()) return null;
        try { return Base64.getDecoder().decode(templateBase64); }
        catch (IllegalArgumentException exception) { throw new IllegalArgumentException("The correction-request template is not valid Base64.", exception); }
    }

    public record RejectionRequest(@NotBlank @Size(max = 500) String reason) {
    }

    public record PasswordChangeRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 12, max = 128) String newPassword,
            @NotBlank @Size(min = 12, max = 128) String newPasswordConfirmation
    ) {
    }

    public record CorrectionRequestPdfRequest(
            Map<String, String> fields,
            @Size(max = 14_000_000) String templateBase64
    ) {
        public CorrectionRequestPdfRequest {
            fields = fields == null ? Map.of() : Map.copyOf(fields);
        }
    }

    public record ApiKeyApplicationResponse(
            String applicationId,
            String partnerId,
            String status,
            java.time.Instant requestedAt,
            java.time.Instant reviewedAt,
            String apiKeySha256Prefix,
            String apiKey,
            String rejectionReason
    ) {
        static ApiKeyApplicationResponse from(PortalApiKeyApplication application, String apiKey) {
            return new ApiKeyApplicationResponse(application.applicationId(), application.partnerId(), application.status().name(),
                    application.requestedAt(), application.reviewedAt(), application.apiKeySha256Prefix(), apiKey,
                    application.rejectionReason());
        }
    }
}

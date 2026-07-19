package com.hana.omniconnect.portal.api;

import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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

import com.hana.omniconnect.common.api.ApiResponse;
import com.hana.omniconnect.portal.PortalAccessService;
import com.hana.omniconnect.portal.PortalAccountService;
import com.hana.omniconnect.portal.PortalApiKeyApplication;
import com.hana.omniconnect.portal.PortalApiKeyApplicationService;
import com.hana.omniconnect.portal.PortalUser;
import com.hana.omniconnect.term.application.KoreanFinancialTermExplanationService;
import com.hana.omniconnect.term.domain.KoreanFinancialTermClickStat;
import com.hana.omniconnect.term.domain.KoreanFinancialTermClickPoint;
import com.hana.omniconnect.tax.refund.TaxRefundBackofficeCase;
import com.hana.omniconnect.tax.refund.TaxRefundBackofficeService;
import com.hana.omniconnect.tax.refund.TaxRefundIdentifiers;
import com.hana.omniconnect.tax.refund.TaxCorrectionRequestPdfService;
import com.hana.omniconnect.tax.refund.TaxRefundDocumentContent;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/portal")
@Tag(name = "Portal", description = "Hana Omni-Connect partner portal and administrator APIs")
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

    @PostMapping("/logout")
    @Operation(summary = "Revoke every active portal session for the signed-in member")
    public ApiResponse<Void> logout(HttpServletRequest request) {
        accountService.logout(accessService.requireUser(request));
        return ApiResponse.success(null);
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
    @Operation(summary = "Request a permanent Hana Omni-Connect partner API key")
    public ApiResponse<ApiKeyApplicationResponse> requestApiKey(HttpServletRequest request) {
        return ApiResponse.success(ApiKeyApplicationResponse.from(applicationService.request(accessService.requireUser(request)), null));
    }

    @GetMapping("/api-key-applications")
    @Operation(summary = "List the signed-in member's API key applications")
    public ApiResponse<List<ApiKeyApplicationResponse>> myApplications(HttpServletRequest request) {
        PortalUser user = accessService.requireUser(request);
        return ApiResponse.success(applicationService.listForUser(user).stream()
                .map(application -> ApiKeyApplicationResponse.from(application, null))
                .toList());
    }

    @PostMapping("/api-key-applications/{applicationId}/reveal")
    @Operation(summary = "Reveal an approved API key once")
    public ResponseEntity<ApiResponse<ApiKeyApplicationResponse>> revealApiKey(
            HttpServletRequest request,
            @PathVariable @Pattern(regexp = "PAPP-[A-Z0-9]{20}") String applicationId) {
        PortalUser user = accessService.requireUser(request);
        PortalApiKeyApplication application = applicationService.find(applicationId);
        String apiKey = applicationService.revealKeyOnce(applicationId, user);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("Pragma", "no-cache")
                .body(ApiResponse.success(ApiKeyApplicationResponse.from(application, apiKey)));
    }

    @PostMapping("/api-key-applications/{applicationId}/cancel")
    @Operation(summary = "Cancel a pending API key request")
    public ApiResponse<ApiKeyApplicationResponse> cancel(
            HttpServletRequest request,
            @PathVariable @Pattern(regexp = "PAPP-[A-Z0-9]{20}") String applicationId) {
        PortalApiKeyApplication application = applicationService.cancel(applicationId, accessService.requireUser(request));
        return ApiResponse.success(ApiKeyApplicationResponse.from(application, null));
    }

    @PostMapping("/api-key-applications/{applicationId}/reissue")
    @Operation(summary = "Request reissue of an approved API key")
    public ApiResponse<ApiKeyApplicationResponse> requestReissue(
            HttpServletRequest request,
            @PathVariable @Pattern(regexp = "PAPP-[A-Z0-9]{20}") String applicationId) {
        PortalApiKeyApplication application = applicationService.requestReissue(applicationId, accessService.requireUser(request));
        return ApiResponse.success(ApiKeyApplicationResponse.from(application, null));
    }

    @PostMapping("/api-key-applications/{applicationId}/revoke")
    @Operation(summary = "Request revocation of an approved API key")
    public ApiResponse<ApiKeyApplicationResponse> requestRevocation(
            HttpServletRequest request,
            @PathVariable @Pattern(regexp = "PAPP-[A-Z0-9]{20}") String applicationId) {
        PortalApiKeyApplication application = applicationService.requestRevocation(applicationId, accessService.requireUser(request));
        return ApiResponse.success(ApiKeyApplicationResponse.from(application, null));
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

    @PostMapping("/admin/api-key-applications/{applicationId}/reissue")
    @Operation(summary = "Immediately reissue an approved API key")
    public ApiResponse<ApiKeyApplicationResponse> reissueNow(
            HttpServletRequest request,
            @PathVariable @Pattern(regexp = "PAPP-[A-Z0-9]{20}") String applicationId) {
        PortalApiKeyApplication application = applicationService.reissueNow(applicationId, accessService.requireAdmin(request));
        return ApiResponse.success(ApiKeyApplicationResponse.from(application, null));
    }

    @PostMapping("/admin/api-key-applications/{applicationId}/revoke")
    @Operation(summary = "Immediately revoke an approved API key")
    public ApiResponse<ApiKeyApplicationResponse> revokeNow(
            HttpServletRequest request,
            @PathVariable @Pattern(regexp = "PAPP-[A-Z0-9]{20}") String applicationId) {
        PortalApiKeyApplication application = applicationService.revokeNow(applicationId, accessService.requireAdmin(request));
        return ApiResponse.success(ApiKeyApplicationResponse.from(application, null));
    }

    @GetMapping("/admin/term-analytics")
    @Operation(summary = "List glossary explanation click analytics for an administrator")
    public ApiResponse<TermAnalyticsResponse> termAnalytics(
            HttpServletRequest request,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "DAY")
            @Pattern(regexp = "DAY|MONTH|YEAR|ALL") String period) {
        accessService.requireAdmin(request);
        return ApiResponse.success(new TermAnalyticsResponse(
                period,
                termExplanationService.clickSeries(period),
                termExplanationService.stats(100)));
    }

    @GetMapping("/admin/tax/refund-cases")
    @Operation(summary = "List synced exchange tax refund cases for an administrator")
    public ApiResponse<List<TaxRefundBackofficeCase>> taxRefundCases(HttpServletRequest request) {
        accessService.requireAdmin(request);
        return ApiResponse.success(taxRefundBackofficeService.list());
    }

    @GetMapping("/admin/tax/correction-request/template/layout")
    @Operation(summary = "Load the trusted correction-request PDF editor layout")
    public ApiResponse<TaxCorrectionRequestPdfService.TemplateLayout> correctionRequestTemplateLayout(
            HttpServletRequest request) {
        accessService.requireAdmin(request);
        return ApiResponse.success(correctionRequestPdfService.templateLayout());
    }

    @GetMapping(value = "/admin/tax/correction-request/template/pages/{pageNumber}", produces = MediaType.IMAGE_PNG_VALUE)
    @Operation(summary = "Render a trusted correction-request PDF template page")
    public ResponseEntity<byte[]> correctionRequestTemplatePage(
            HttpServletRequest request,
            @PathVariable @Min(1) @Max(2) int pageNumber) {
        accessService.requireAdmin(request);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.IMAGE_PNG)
                .body(correctionRequestPdfService.templatePage(pageNumber));
    }

    @GetMapping("/admin/tax/refund-cases/{caseId}/correction-fields")
    @Operation(summary = "Load verified document values into a correction-request editor")
    public ApiResponse<Map<String, String>> correctionFields(
            HttpServletRequest request,
            @PathVariable @Pattern(regexp = TaxRefundIdentifiers.CASE_ID_PATTERN) String caseId) {
        accessService.requireAdmin(request);
        return ApiResponse.success(taxRefundBackofficeService.initialCorrectionFields(caseId));
    }

    @GetMapping("/admin/tax/refund-cases/{caseId}/correction-request")
    @Operation(summary = "Load the saved correction-request editor values")
    public ApiResponse<Map<String, String>> savedCorrectionFields(
            HttpServletRequest request,
            @PathVariable @Pattern(regexp = TaxRefundIdentifiers.CASE_ID_PATTERN) String caseId) {
        accessService.requireAdmin(request);
        return ApiResponse.success(taxRefundBackofficeService.savedCorrectionFields(caseId));
    }

    @GetMapping("/admin/tax/refund-cases/{caseId}/documents/{documentId}")
    @Operation(summary = "View a verified tax document")
    public ResponseEntity<byte[]> taxDocument(
            HttpServletRequest request,
            @PathVariable @Pattern(regexp = TaxRefundIdentifiers.CASE_ID_PATTERN) String caseId,
            @PathVariable @Pattern(regexp = TaxRefundIdentifiers.DOCUMENT_ID_PATTERN) String documentId) {
        accessService.requireAdmin(request);
        TaxRefundDocumentContent document = taxRefundBackofficeService.documentContent(caseId, documentId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + document.documentId() + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(document.contentType()))
                .body(document.content());
    }

    @PostMapping("/admin/tax/refund-cases/{caseId}/correction-request")
    @Operation(summary = "Save correction-request editor values")
    public ApiResponse<Map<String, String>> saveCorrectionRequest(
            HttpServletRequest request,
            @PathVariable @Pattern(regexp = TaxRefundIdentifiers.CASE_ID_PATTERN) String caseId,
            @Valid @RequestBody CorrectionRequestPdfRequest body) {
        PortalUser administrator = accessService.requireAdmin(request);
        byte[] pdf = correctionRequestPdfService.render(body.fields());
        taxRefundBackofficeService.savePreparedCorrection(caseId, body.fields(), pdf, administrator.userId());
        return ApiResponse.success(body.fields());
    }

    @PostMapping(value = "/admin/tax/refund-cases/{caseId}/correction-request.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Render a correction request PDF from verified values and administrator edits")
    public ResponseEntity<byte[]> correctionRequestPdf(
            HttpServletRequest request,
            @PathVariable @Pattern(regexp = TaxRefundIdentifiers.CASE_ID_PATTERN) String caseId,
            @Valid @RequestBody CorrectionRequestPdfRequest body) {
        PortalUser administrator = accessService.requireAdmin(request);
        taxRefundBackofficeService.caseById(caseId);
        byte[] pdf = correctionRequestPdfService.render(body.fields());
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
            @PathVariable @Pattern(regexp = TaxRefundIdentifiers.CASE_ID_PATTERN) String caseId,
            @Valid @RequestBody CorrectionRequestPdfRequest body) {
        PortalUser administrator = accessService.requireAdmin(request);
        byte[] pdf = correctionRequestPdfService.render(body.fields());
        taxRefundBackofficeService.savePreparedCorrection(caseId, body.fields(), pdf, administrator.userId());
        return ApiResponse.success(taxRefundBackofficeService.approve(caseId, administrator.userId()));
    }

    public record RejectionRequest(@NotBlank @Size(max = 500) String reason) {
    }

    public record TermAnalyticsResponse(
            String period,
            List<KoreanFinancialTermClickPoint> points,
            List<KoreanFinancialTermClickStat> terms
    ) {
    }

    public record PasswordChangeRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 12, max = 128) String newPassword,
            @NotBlank @Size(min = 12, max = 128) String newPasswordConfirmation
    ) {
    }

    public record CorrectionRequestPdfRequest(
            Map<String, String> fields
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

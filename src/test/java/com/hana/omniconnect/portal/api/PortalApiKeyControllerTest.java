package com.hana.omniconnect.portal.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.hana.omniconnect.tax.refund.TaxRefundBackofficeService;
import com.hana.omniconnect.tax.refund.TaxRefundCaseSyncRequest;
import com.hana.omniconnect.tax.refund.TaxRefundDocumentSnapshot;

@SpringBootTest(properties = {
        "omni-connect.portal.session-signing-key=portal-test-signing-key-should-be-secret",
        "omni-connect.portal.api-key-encryption-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "omni-connect.security.cors-allowed-origins[0]=http://localhost:5173",
        "omni-connect.alert.dedupe.mode=in-memory",
        "management.health.redis.enabled=false"
})
@AutoConfigureMockMvc
class PortalApiKeyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TaxRefundBackofficeService taxRefundBackofficeService;

    @BeforeEach
    void resetAdminPassword() {
        String passwordHash = "{bcrypt}$2y$12$QYdm5Z2QBMF/9XgtNMvA5umnErMvlTRskDzg4U5wcIN5PH.X9Sf/K";
        int updated = jdbcTemplate.update(
                "UPDATE portal_users SET password_hash = ?, role = 'ADMIN', password_change_required = TRUE, session_version = 0, password_changed_at = NULL WHERE username = 'admin'",
                passwordHash);
        if (updated == 0) {
            jdbcTemplate.update("""
                    INSERT INTO portal_users (
                        user_id, username, password_hash, display_name, phone_number, role,
                        created_at, updated_at, password_change_required, session_version, password_changed_at
                    ) VALUES (?, 'admin', ?, 'Test Admin', '', 'ADMIN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, TRUE, 0, NULL)
                    """, "PUSR-ADMIN000000000000000", passwordHash);
        }
        jdbcTemplate.update("DELETE FROM tax_refund_backoffice_cases WHERE case_id = 'TAX-ABCDEFGHIJKL'");
    }

    @Test
    void memberRequestsAndAdministratorApprovesPermanentPartnerApiKey() throws Exception {
        String memberToken = token(postJson("/api/v1/portal/auth/sign-up", """
                {"username":"localbroker","password":"LocalBrokerPassword1!","passwordConfirmation":"LocalBrokerPassword1!","name":"Local Broker","phoneNumber":"+1 212 555 0100"}
                """));

        String applicationId = applicationId(mockMvc.perform(post("/api/v1/portal/api-key-applications")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.applicantUsername").value("localbroker"))
                .andExpect(jsonPath("$.data.applicantName").value("Local Broker"))
                .andReturn().getResponse().getContentAsString());

        String initialAdminToken = token(postJson("/api/v1/portal/auth/login", """
                {"username":"admin","password":"admin"}
                """));
        String adminToken = token(mockMvc.perform(post("/api/v1/portal/me/password")
                .header("Authorization", "Bearer " + initialAdminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"currentPassword":"admin","newPassword":"PortalAdminPassword1!","newPasswordConfirmation":"PortalAdminPassword1!"}
                        """)));

        mockMvc.perform(post("/api/v1/portal/admin/api-key-applications/{id}/approve", applicationId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.apiKey").doesNotExist());

        mockMvc.perform(get("/api/v1/portal/admin/api-key-applications")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].applicantUsername").value("localbroker"))
                .andExpect(jsonPath("$.data[0].applicantName").value("Local Broker"));

        mockMvc.perform(post("/api/v1/portal/api-key-applications/{id}/reveal", applicationId)
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/portal/api-key-applications/{id}/reveal", applicationId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"PortalAdminPassword1!\"}"))
                .andExpect(status().isForbidden());

        String revealedApplication = mockMvc.perform(post(
                        "/api/v1/portal/api-key-applications/{id}/reveal", applicationId)
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"LocalBrokerPassword1!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.apiKey").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String apiKey = objectMapper.readTree(revealedApplication).path("data").path("apiKey").asText();

        mockMvc.perform(post("/api/v1/portal/api-key-applications/{id}/reveal", applicationId)
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"LocalBrokerPassword1!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.apiKey").value(apiKey));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT encrypted_api_key IS NOT NULL FROM partner_api_key_applications WHERE application_id = ?",
                Boolean.class,
                applicationId)).isTrue();

        mockMvc.perform(get("/api/v1/unknown").header("X-HANA-OMNI-CONNECT-API-KEY", apiKey))
                .andExpect(status().isNotFound());
    }

    @Test
    void memberAndAdministratorManageApiKeyLifecycle() throws Exception {
        String memberToken = token(postJson("/api/v1/portal/auth/sign-up", """
                {"username":"key-lifecycle","password":"LongEight8!x","passwordConfirmation":"LongEight8!x","name":"Key Lifecycle","phoneNumber":"+82 10-1234-5678"}
                """));
        String cancelledId = applicationId(mockMvc.perform(post("/api/v1/portal/api-key-applications")
                        .header("Authorization", "Bearer " + memberToken))
                .andReturn().getResponse().getContentAsString());
        mockMvc.perform(post("/api/v1/portal/api-key-applications/{id}/cancel", cancelledId)
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        String applicationId = applicationId(mockMvc.perform(post("/api/v1/portal/api-key-applications")
                        .header("Authorization", "Bearer " + memberToken))
                .andReturn().getResponse().getContentAsString());
        String initialAdminToken = token(postJson("/api/v1/portal/auth/login", """
                {"username":"admin","password":"admin"}
                """));
        String adminToken = token(mockMvc.perform(post("/api/v1/portal/me/password")
                .header("Authorization", "Bearer " + initialAdminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"currentPassword":"admin","newPassword":"LifecycleAdmin1!","newPasswordConfirmation":"LifecycleAdmin1!"}
                        """)));
        mockMvc.perform(post("/api/v1/portal/admin/api-key-applications/{id}/approve", applicationId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));

        mockMvc.perform(post("/api/v1/portal/api-key-applications/{id}/reissue", applicationId)
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REISSUE_REQUESTED"));
        mockMvc.perform(post("/api/v1/portal/admin/api-key-applications/{id}/reject", applicationId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"재발급 사유 확인 필요\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.rejectionReason").value("재발급 사유 확인 필요"));

        mockMvc.perform(post("/api/v1/portal/api-key-applications/{id}/revoke", applicationId)
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REVOCATION_REQUESTED"));
        mockMvc.perform(post("/api/v1/portal/admin/api-key-applications/{id}/approve", applicationId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REVOKED"));
    }

    @Test
    void portalCorsAndRolePolicyProtectBrowserRequests() throws Exception {
        mockMvc.perform(options("/api/v1/portal/api-key-applications")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().string("Access-Control-Allow-Headers", org.hamcrest.Matchers.containsStringIgnoringCase("Authorization")));

        mockMvc.perform(post("/api/v1/portal/auth/sign-up")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"mismatch-user","password":"LongPasswordValue1!","passwordConfirmation":"DifferentPassword1!","name":"Mismatch","phoneNumber":"+82 10 0000 0000"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PORTAL_006"));

        String memberToken = token(postJson("/api/v1/portal/auth/sign-up", """
                {"username":"role-member","password":"RoleMemberPassword1!","passwordConfirmation":"RoleMemberPassword1!","name":"Role Member","phoneNumber":"+82 10 1111 2222"}
                """));
        mockMvc.perform(get("/api/v1/portal/admin/api-key-applications")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void logoutRevokesOnlyCurrentPortalSession() throws Exception {
        String firstToken = token(postJson("/api/v1/portal/auth/sign-up", """
                {"username":"multi-session-member","password":"MultiSessionPassword1!","passwordConfirmation":"MultiSessionPassword1!","name":"Multi Session","phoneNumber":"+82 10 2222 3333"}
                """));
        String secondToken = token(postJson("/api/v1/portal/auth/login", """
                {"username":"multi-session-member","password":"MultiSessionPassword1!"}
                """));

        mockMvc.perform(post("/api/v1/portal/logout")
                        .header("Authorization", "Bearer " + firstToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/portal/me")
                        .header("Authorization", "Bearer " + firstToken))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/portal/me")
                        .header("Authorization", "Bearer " + secondToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("multi-session-member"));
    }

    @Test
    void administratorPreparesCorrectionPdfAndApprovesMemberRefund() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO tax_refund_backoffice_cases (
                    case_id, account_id, user_id, tax_year, treaty_country, estimated_refund_usd,
                    advance_payment_requested, advance_payment_eligible, matched_trade_ids_json,
                    verified_documents_json, status, requested_at, synced_at, tax_office_submission_status
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, ?)
                """,
                "TAX-ABCDEFGHIJKL", "ACC-ABCDEFGHIJKL", "USR-ABCDEFGHIJKL", 2025, "US", "120.00",
                true, true, "[]",
                "[{\"documentId\":\"DOC-1\",\"documentType\":\"RESIDENCE_CERTIFICATE\",\"fileName\":\"residence.pdf\",\"extractedFields\":{\"taxpayer_name\":\"Jane Doe\",\"residency_country_code\":\"US\"}}]",
                "SYNCED_WITH_HANA", "NOT_SUBMITTED");

        String initialToken = token(postJson("/api/v1/portal/auth/login", """
                {"username":"admin","password":"admin"}
                """));
        mockMvc.perform(get("/api/v1/portal/admin/tax/refund-cases")
                        .header("Authorization", "Bearer " + initialToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PORTAL_007"));

        String adminToken = token(mockMvc.perform(post("/api/v1/portal/me/password")
                .header("Authorization", "Bearer " + initialToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"currentPassword":"admin","newPassword":"ChangedAdminPassword1!","newPasswordConfirmation":"ChangedAdminPassword1!"}
                        """)));

        mockMvc.perform(get("/api/v1/portal/me").header("Authorization", "Bearer " + initialToken))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/portal/admin/tax/refund-cases/TAX-ABCDEFGHIJKL/correction-fields")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.claimantName").value("Jane Doe"))
                .andExpect(jsonPath("$.data.residencyCountryCode").value("US"));

        mockMvc.perform(get("/api/v1/portal/admin/tax/correction-request/template/layout")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pageWidth").value(595.0))
                .andExpect(jsonPath("$.data.pageHeight").value(841.0))
                .andExpect(jsonPath("$.data.pageCount").value(2))
                .andExpect(jsonPath("$.data.fields[2].key").value("claimantName"));
        mockMvc.perform(get("/api/v1/portal/admin/tax/correction-request/template/pages/1")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"));
        mockMvc.perform(get("/api/v1/portal/admin/tax/refund-cases/TAX-ABCDEFGHIJKLMNOPQRST/correction-fields")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_002"));

        String correctionBody = """
                {"fields":{"claimantName":"Jane Doe","taxYear":"2025","estimatedRefundUsd":"120.00"}}
                """;
        mockMvc.perform(post("/api/v1/portal/admin/tax/refund-cases/TAX-ABCDEFGHIJKL/correction-request.pdf")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(correctionBody))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"));

        mockMvc.perform(post("/api/v1/portal/admin/tax/refund-cases/TAX-ABCDEFGHIJKL/approve")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(correctionBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REFUND_APPROVED"))
                .andExpect(jsonPath("$.data.correctionRequestStatus").value("APPROVED"))
                .andExpect(jsonPath("$.data.correctionPdfSha256").isNotEmpty());

        assertThat(taxRefundBackofficeService.sync(new TaxRefundCaseSyncRequest(
                "TAX-ABCDEFGHIJKL", "ACC-ABCDEFGHIJKL", "USR-ABCDEFGHIJKL", 2025, "US", "120.00",
                true, true, List.of(), List.of(
                        new TaxRefundDocumentSnapshot("DOC-1", "RESIDENCE_CERTIFICATE", "residence.pdf", Map.of("taxpayer_name", "Jane Doe")),
                        new TaxRefundDocumentSnapshot("DOC-2", "APOSTILLE", "apostille.pdf", Map.of()),
                        new TaxRefundDocumentSnapshot("DOC-3", "REDUCED_TAX_APPLICATION", "application.pdf", Map.of())),
                Instant.now())).status()).isEqualTo("REFUND_APPROVED");
    }

    private org.springframework.test.web.servlet.ResultActions postJson(String path, String body) throws Exception {
        return mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String token(org.springframework.test.web.servlet.ResultActions action) throws Exception {
        String payload = action.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(payload).path("data").path("accessToken").asText();
    }

    private String applicationId(String payload) throws Exception {
        JsonNode node = objectMapper.readTree(payload);
        return node.path("data").path("applicationId").asText();
    }
}

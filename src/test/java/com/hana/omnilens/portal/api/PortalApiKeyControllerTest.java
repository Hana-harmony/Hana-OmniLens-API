package com.hana.omnilens.portal.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "omnilens.portal.session-signing-key=portal-test-signing-key-should-be-secret",
        "omnilens.portal.api-key-encryption-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "omnilens.portal.bootstrap-admin-username=portaladmin",
        "omnilens.portal.bootstrap-admin-password=PortalAdminPassword1!",
        "omnilens.alert.dedupe.mode=in-memory",
        "management.health.redis.enabled=false"
})
@AutoConfigureMockMvc
class PortalApiKeyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void memberRequestsAndAdministratorApprovesPermanentPartnerApiKey() throws Exception {
        String memberToken = token(postJson("/api/v1/portal/auth/sign-up", """
                {"username":"localbroker","password":"LocalBrokerPassword1!","name":"Local Broker","phoneNumber":"+1 212 555 0100"}
                """));

        String applicationId = applicationId(mockMvc.perform(post("/api/v1/portal/api-key-applications")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString());

        String adminToken = token(postJson("/api/v1/portal/auth/login", """
                {"username":"portaladmin","password":"PortalAdminPassword1!"}
                """));

        mockMvc.perform(post("/api/v1/portal/admin/api-key-applications/{id}/approve", applicationId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.apiKey").doesNotExist());

        String memberApplications = mockMvc.perform(get("/api/v1/portal/api-key-applications")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].apiKey").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String apiKey = objectMapper.readTree(memberApplications).path("data").get(0).path("apiKey").asText();

        mockMvc.perform(get("/api/v1/unknown").header("X-HANA-OMNILENS-API-KEY", apiKey))
                .andExpect(status().isNotFound());
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

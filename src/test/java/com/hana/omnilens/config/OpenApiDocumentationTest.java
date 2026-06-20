package com.hana.omnilens.config;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "omnilens.security.api-key-enabled=true",
        "omnilens.security.api-key-sha256="
})
@AutoConfigureMockMvc
class OpenApiDocumentationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void openApiDocsExposeBusinessApiWithoutApiKey() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title", equalTo("Hana OmniLens API")))
                .andExpect(jsonPath("$.paths['/api/v1/market/stocks/{stockCode}/quote']", notNullValue()))
                .andExpect(jsonPath("$.paths['/api/v1/market/quotes']", notNullValue()))
                .andExpect(jsonPath("$.paths['/api/v1/market/stocks/{stockCode}/orderability']", notNullValue()))
                .andExpect(jsonPath("$.paths['/api/v1/market/stocks/{stockCode}/history']", notNullValue()))
                .andExpect(jsonPath("$.paths['/api/v1/security/partners/{partnerId}/credentials/rotate']",
                        notNullValue()))
                .andExpect(jsonPath("$.paths['/api/v1/tax/refund-cases/sync']", notNullValue()))
                .andExpect(jsonPath("$.paths['/api/v1/tax/refund-cases/classify']", notNullValue()))
                .andExpect(jsonPath("$.paths['/api/v1/tax/rectification-batches/{taxYear}/quarters/{quarter}']",
                        notNullValue()))
                .andExpect(jsonPath("$.components.securitySchemes.hanaApiKey", notNullValue()));
    }
}

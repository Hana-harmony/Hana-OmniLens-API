package com.hana.omniconnect.security;

import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.hana.omniconnect.security.ApiKeyRateLimiter.RateLimitDecision;

@SpringBootTest(properties = {
        "omni-connect.alert.dedupe.mode=in-memory",
        "management.health.redis.enabled=false"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class ApiKeyRateLimitFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUpPartnerCredential() {
        com.hana.omniconnect.support.PartnerCredentialTestData.replace(
                jdbcTemplate, "partner-rate-limit", "test-api-key");
    }

    @MockitoBean
    private ApiKeyRateLimiter rateLimiter;

    @Test
    void returnsTooManyRequestsWhenApiKeyBucketIsEmpty() throws Exception {
        when(rateLimiter.consume(anyString()))
                .thenReturn(RateLimitDecision.accepted(), RateLimitDecision.rejected(3600));

        mockMvc.perform(get("/openapi.yaml")
                        .header("X-HANA-OMNI-CONNECT-API-KEY", "test-api-key"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/openapi.yaml")
                        .header("X-HANA-OMNI-CONNECT-API-KEY", "test-api-key"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", equalTo("3600")));
    }

    @Test
    void publicHealthEndpointDoesNotConsumeRateLimit() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());

        verifyNoInteractions(rateLimiter);

        when(rateLimiter.consume(anyString())).thenReturn(RateLimitDecision.accepted());

        mockMvc.perform(get("/openapi.yaml")
                        .header("X-HANA-OMNI-CONNECT-API-KEY", "test-api-key"))
                .andExpect(status().isOk());
    }
}

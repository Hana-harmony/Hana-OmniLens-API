package com.hana.omnilens.security;

import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "omnilens.security.api-key-sha256=4c806362b613f7496abf284146efd31da90e4b16169fe001841ca17290f427c4",
        "omnilens.security.rate-limit.enabled=true",
        "omnilens.security.rate-limit.capacity=1",
        "omnilens.security.rate-limit.refill-tokens=1",
        "omnilens.security.rate-limit.refill-period=1h",
        "omnilens.alert.dedupe.mode=in-memory",
        "management.health.redis.enabled=false"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class ApiKeyRateLimitFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsTooManyRequestsWhenApiKeyBucketIsEmpty() throws Exception {
        mockMvc.perform(get("/openapi.yaml")
                        .header("X-HANA-OMNILENS-API-KEY", "test-api-key"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/openapi.yaml")
                        .header("X-HANA-OMNILENS-API-KEY", "test-api-key"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", equalTo("3600")));
    }

    @Test
    void publicHealthEndpointDoesNotConsumeRateLimit() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/openapi.yaml")
                        .header("X-HANA-OMNILENS-API-KEY", "test-api-key"))
                .andExpect(status().isOk());
    }
}

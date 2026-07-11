package com.hana.omnilens.config;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.cert.CertificateExpiredException;
import java.security.cert.X509Certificate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "omnilens.security.api-key-sha256=4c806362b613f7496abf284146efd31da90e4b16169fe001841ca17290f427c4",
        "omnilens.security.rate-limit.enabled=false",
        "omnilens.security.mtls.enabled=true",
        "omnilens.alert.dedupe.mode=in-memory",
        "management.health.redis.enabled=false"
})
@AutoConfigureMockMvc
class MtlsClientCertificateFilterTest {

    private static final String API_KEY = "test-api-key";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void rejectsProtectedApiWithoutClientCertificate() throws Exception {
        mockMvc.perform(get("/openapi.yaml")
                        .header("X-HANA-OMNILENS-API-KEY", API_KEY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void acceptsProtectedApiWithValidClientCertificate() throws Exception {
        mockMvc.perform(get("/openapi.yaml")
                        .header("X-HANA-OMNILENS-API-KEY", API_KEY)
                        .requestAttr(
                                MtlsClientCertificateFilter.CLIENT_CERTIFICATE_ATTRIBUTE,
                                new X509Certificate[] {mock(X509Certificate.class)}))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsExpiredClientCertificate() throws Exception {
        X509Certificate certificate = mock(X509Certificate.class);
        doThrow(new CertificateExpiredException("expired")).when(certificate).checkValidity();

        mockMvc.perform(get("/openapi.yaml")
                        .header("X-HANA-OMNILENS-API-KEY", API_KEY)
                        .requestAttr(
                                MtlsClientCertificateFilter.CLIENT_CERTIFICATE_ATTRIBUTE,
                                new X509Certificate[] {certificate}))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void healthEndpointDoesNotRequireClientCertificate() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }
}

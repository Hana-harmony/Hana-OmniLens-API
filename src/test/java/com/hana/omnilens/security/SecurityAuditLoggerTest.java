package com.hana.omnilens.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;

class SecurityAuditLoggerTest {

    private final SecurityAuditLogger securityAuditLogger = new SecurityAuditLogger();

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void recordLogsFingerprintPrefixWithoutRawCredential(CapturedOutput output) {
        String fingerprint = "1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef";
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/market/stocks/005930/quote");

        securityAuditLogger.record(request, "success", fingerprint, "authenticated");

        assertThat(output).contains("security_audit outcome=success");
        assertThat(output).contains("apiKeyFingerprint=1234567890ab");
        assertThat(output).doesNotContain(fingerprint);
    }
}

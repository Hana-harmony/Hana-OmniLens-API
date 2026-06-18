package com.hana.omnilens.security;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class SecurityAuditLogger {

    private static final Logger AUDIT_LOGGER = LoggerFactory.getLogger("com.hana.omnilens.audit.security");
    private static final int FINGERPRINT_PREFIX_LENGTH = 12;

    public void record(HttpServletRequest request, String outcome, String apiKeyFingerprint, String reason) {
        AUDIT_LOGGER.info(
                "security_audit outcome={} method={} path={} apiKeyFingerprint={} reason={}",
                outcome,
                request.getMethod(),
                request.getRequestURI(),
                fingerprintPrefix(apiKeyFingerprint),
                reason);
    }

    private String fingerprintPrefix(String apiKeyFingerprint) {
        if (!StringUtils.hasText(apiKeyFingerprint)) {
            return "missing";
        }
        return apiKeyFingerprint.substring(0, Math.min(FINGERPRINT_PREFIX_LENGTH, apiKeyFingerprint.length()));
    }
}

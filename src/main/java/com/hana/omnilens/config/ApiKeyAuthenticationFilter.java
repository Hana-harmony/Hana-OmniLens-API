package com.hana.omnilens.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import com.hana.omnilens.common.api.ApiResponse;
import com.hana.omnilens.common.exception.ErrorCode;
import com.hana.omnilens.security.ApiKeyRateLimiter;
import com.hana.omnilens.security.ApiKeyRateLimiter.RateLimitDecision;
import com.hana.omnilens.security.ApiRequestSignatureVerifier;
import com.hana.omnilens.security.ApiRequestSignatureVerifier.SignatureVerificationResult;
import com.hana.omnilens.security.CachedBodyHttpServletRequest;
import com.hana.omnilens.security.PartnerAuthentication;
import com.hana.omnilens.security.PartnerCredential;
import com.hana.omnilens.security.PartnerCredentialRepository;
import com.hana.omnilens.security.SecurityAuditLogger;

@Component
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER_NAME = "X-HANA-OMNILENS-API-KEY";

    private final OmniLensSecurityProperties properties;
    private final ApiKeyRateLimiter apiKeyRateLimiter;
    private final ApiRequestSignatureVerifier apiRequestSignatureVerifier;
    private final SecurityAuditLogger securityAuditLogger;
    private final PartnerCredentialRepository partnerCredentialRepository;
    private final ObjectMapper objectMapper;

    public ApiKeyAuthenticationFilter(
            OmniLensSecurityProperties properties,
            ApiKeyRateLimiter apiKeyRateLimiter,
            ApiRequestSignatureVerifier apiRequestSignatureVerifier,
            SecurityAuditLogger securityAuditLogger,
            PartnerCredentialRepository partnerCredentialRepository,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.apiKeyRateLimiter = apiKeyRateLimiter;
        this.apiRequestSignatureVerifier = apiRequestSignatureVerifier;
        this.securityAuditLogger = securityAuditLogger;
        this.partnerCredentialRepository = partnerCredentialRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (isPublicEndpoint(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        CachedBodyHttpServletRequest cachedRequest = new CachedBodyHttpServletRequest(request);

        String providedKey = request.getHeader(HEADER_NAME);
        String providedKeyHash = StringUtils.hasText(providedKey) ? sha256Hex(providedKey) : "";
        if (!StringUtils.hasText(providedKey)) {
            securityAuditLogger.record(cachedRequest, "failure", providedKeyHash, "invalid_api_key");
            writeError(response, ErrorCode.INVALID_API_KEY);
            return;
        }

        ApiKeyAuthentication authentication;
        try {
            authentication = authenticate(providedKeyHash);
        } catch (CredentialStoreUnavailableException exception) {
            securityAuditLogger.record(cachedRequest, "failure", providedKeyHash, "credential_store_unavailable");
            writeError(response, ErrorCode.API_KEY_NOT_CONFIGURED);
            return;
        }

        if (!authentication.configured()) {
            securityAuditLogger.record(cachedRequest, "failure", providedKeyHash, "api_key_hash_missing");
            writeError(response, ErrorCode.API_KEY_NOT_CONFIGURED);
            return;
        }
        if (!authentication.authenticated()) {
            securityAuditLogger.record(cachedRequest, "failure", providedKeyHash, "invalid_api_key");
            writeError(response, ErrorCode.INVALID_API_KEY);
            return;
        }

        RateLimitDecision decision;
        try {
            decision = apiKeyRateLimiter.consume(providedKeyHash);
        } catch (RuntimeException exception) {
            securityAuditLogger.record(cachedRequest, "failure", providedKeyHash, "rate_limit_store_unavailable");
            writeError(response, ErrorCode.SECURITY_SERVICE_UNAVAILABLE);
            return;
        }
        if (!decision.allowed()) {
            securityAuditLogger.record(cachedRequest, "failure", providedKeyHash, "rate_limit_exceeded");
            response.setHeader("Retry-After", String.valueOf(decision.retryAfterSeconds()));
            writeError(response, HttpStatus.TOO_MANY_REQUESTS, "AUTH_003", "Rate limit exceeded");
            return;
        }

        SignatureVerificationResult signature = apiRequestSignatureVerifier.verify(
                cachedRequest,
                providedKeyHash,
                providedKey,
                cachedRequest.body());
        if (!signature.valid()) {
            securityAuditLogger.record(cachedRequest, "failure", providedKeyHash, "invalid_signature");
            writeError(response, signature.status(), "AUTH_004", signature.message());
            return;
        }

        securityAuditLogger.record(cachedRequest, "success", providedKeyHash, "authenticated");
        cachedRequest.setAttribute(PartnerAuthentication.API_KEY_FINGERPRINT_ATTRIBUTE, providedKeyHash);
        authentication.partnerId()
                .ifPresent(partnerId -> cachedRequest.setAttribute(
                        PartnerAuthentication.PARTNER_ID_ATTRIBUTE,
                        partnerId));
        cachedRequest.setAttribute(PartnerAuthentication.PARTNER_IDS_ATTRIBUTE, authentication.partnerIds());
        cachedRequest.setAttribute(PartnerAuthentication.BOOTSTRAP_ATTRIBUTE, authentication.bootstrap());
        filterChain.doFilter(cachedRequest, response);
    }

    private boolean isPublicEndpoint(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator/health")
                || path.equals("/actuator/info")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/api/v1/portal/");
    }

    private void writeError(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        writeError(response, errorCode.status(), errorCode.code(), errorCode.message());
    }

    private void writeError(HttpServletResponse response, HttpStatus status, String code, String message)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), ApiResponse.error(status.value(), code, message));
    }

    private ApiKeyAuthentication authenticate(String providedKeyHash) {
        if (matchesConfiguredHash(providedKeyHash)) {
			if (properties.apiKeyPartnerIds().isEmpty()) {
                return ApiKeyAuthentication.notConfigured();
            }
			return ApiKeyAuthentication.bootstrap(properties.apiKeyPartnerIds());
        }
        Optional<PartnerCredential> partnerCredential;
        boolean hasActiveCredential;
        try {
            partnerCredential = partnerCredentialRepository.findActiveByApiKeySha256(providedKeyHash);
            hasActiveCredential = partnerCredential.isPresent() || partnerCredentialRepository.existsAnyActive();
        } catch (RuntimeException exception) {
            throw new CredentialStoreUnavailableException(exception);
        }

        if (partnerCredential.isPresent()) {
            return ApiKeyAuthentication.partner(partnerCredential.get().partnerId());
        }
        if (!StringUtils.hasText(properties.apiKeySha256()) && !hasActiveCredential) {
            return ApiKeyAuthentication.notConfigured();
        }
        return ApiKeyAuthentication.invalid();
    }

    private boolean matchesConfiguredHash(String providedKeyHash) {
        if (!StringUtils.hasText(properties.apiKeySha256())) {
            return false;
        }
        byte[] expected = properties.apiKeySha256().trim().getBytes(StandardCharsets.UTF_8);
        byte[] actual = providedKeyHash.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(actual, expected);
    }

    private String sha256Hex(String rawValue) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawValue.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private record ApiKeyAuthentication(
            boolean configured,
            boolean authenticated,
            Optional<String> partnerId,
			java.util.List<String> partnerIds,
            boolean bootstrap
    ) {

		private static ApiKeyAuthentication bootstrap(java.util.List<String> partnerIds) {
			Optional<String> primary = partnerIds.size() == 1 ? Optional.of(partnerIds.get(0)) : Optional.empty();
			return new ApiKeyAuthentication(true, true, primary, partnerIds, true);
        }

        private static ApiKeyAuthentication partner(String partnerId) {
			return new ApiKeyAuthentication(true, true, Optional.of(partnerId), java.util.List.of(partnerId), false);
        }

        private static ApiKeyAuthentication invalid() {
			return new ApiKeyAuthentication(true, false, Optional.empty(), java.util.List.of(), false);
        }

        private static ApiKeyAuthentication notConfigured() {
			return new ApiKeyAuthentication(false, false, Optional.empty(), java.util.List.of(), false);
        }

    }

    private static class CredentialStoreUnavailableException extends RuntimeException {

        CredentialStoreUnavailableException(RuntimeException cause) {
            super(cause);
        }
    }
}

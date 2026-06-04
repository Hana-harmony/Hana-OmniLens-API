package com.hana.omnilens.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

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

    public ApiKeyAuthenticationFilter(
            OmniLensSecurityProperties properties,
            ApiKeyRateLimiter apiKeyRateLimiter,
            ApiRequestSignatureVerifier apiRequestSignatureVerifier,
            SecurityAuditLogger securityAuditLogger,
            PartnerCredentialRepository partnerCredentialRepository) {
        this.properties = properties;
        this.apiKeyRateLimiter = apiKeyRateLimiter;
        this.apiRequestSignatureVerifier = apiRequestSignatureVerifier;
        this.securityAuditLogger = securityAuditLogger;
        this.partnerCredentialRepository = partnerCredentialRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!properties.apiKeyEnabled() || isPublicEndpoint(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        CachedBodyHttpServletRequest cachedRequest = new CachedBodyHttpServletRequest(request);

        String providedKey = request.getHeader(HEADER_NAME);
        String providedKeyHash = StringUtils.hasText(providedKey) ? sha256Hex(providedKey) : "";
        if (!StringUtils.hasText(providedKey)) {
            securityAuditLogger.record(cachedRequest, "failure", providedKeyHash, "invalid_api_key");
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "Invalid API key");
            return;
        }

        ApiKeyAuthentication authentication;
        try {
            authentication = authenticate(providedKeyHash);
        } catch (CredentialStoreUnavailableException exception) {
            securityAuditLogger.record(cachedRequest, "failure", providedKeyHash, "credential_store_unavailable");
            response.sendError(HttpStatus.SERVICE_UNAVAILABLE.value(), "API credential store is unavailable");
            return;
        }

        if (!authentication.configured()) {
            securityAuditLogger.record(cachedRequest, "failure", providedKeyHash, "api_key_hash_missing");
            response.sendError(HttpStatus.SERVICE_UNAVAILABLE.value(), "API key hash is not configured");
            return;
        }
        if (!authentication.authenticated()) {
            securityAuditLogger.record(cachedRequest, "failure", providedKeyHash, "invalid_api_key");
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "Invalid API key");
            return;
        }

        RateLimitDecision decision = apiKeyRateLimiter.consume(providedKeyHash);
        if (!decision.allowed()) {
            securityAuditLogger.record(cachedRequest, "failure", providedKeyHash, "rate_limit_exceeded");
            response.setHeader("Retry-After", String.valueOf(decision.retryAfterSeconds()));
            response.sendError(HttpStatus.TOO_MANY_REQUESTS.value(), "Rate limit exceeded");
            return;
        }

        SignatureVerificationResult signature = apiRequestSignatureVerifier.verify(
                cachedRequest,
                providedKeyHash,
                cachedRequest.body());
        if (!signature.valid()) {
            securityAuditLogger.record(cachedRequest, "failure", providedKeyHash, "invalid_signature");
            response.sendError(signature.status().value(), signature.message());
            return;
        }

        securityAuditLogger.record(cachedRequest, "success", providedKeyHash, "authenticated");
        cachedRequest.setAttribute(PartnerAuthentication.API_KEY_FINGERPRINT_ATTRIBUTE, providedKeyHash);
        authentication.partnerId()
                .ifPresent(partnerId -> cachedRequest.setAttribute(
                        PartnerAuthentication.PARTNER_ID_ATTRIBUTE,
                        partnerId));
        filterChain.doFilter(cachedRequest, response);
    }

    private boolean isPublicEndpoint(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator/health") || path.equals("/actuator/info");
    }

    private boolean matchesConfiguredHash(String providedKeyHash) {
        if (!StringUtils.hasText(properties.apiKeySha256())) {
            return false;
        }
        byte[] expected = properties.apiKeySha256().trim().getBytes(StandardCharsets.UTF_8);
        byte[] actual = providedKeyHash.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(actual, expected);
    }

    private ApiKeyAuthentication authenticate(String providedKeyHash) {
        if (matchesConfiguredHash(providedKeyHash)) {
            return ApiKeyAuthentication.globalFallback();
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
            Optional<String> partnerId
    ) {

        private static ApiKeyAuthentication globalFallback() {
            return new ApiKeyAuthentication(true, true, Optional.empty());
        }

        private static ApiKeyAuthentication partner(String partnerId) {
            return new ApiKeyAuthentication(true, true, Optional.of(partnerId));
        }

        private static ApiKeyAuthentication invalid() {
            return new ApiKeyAuthentication(true, false, Optional.empty());
        }

        private static ApiKeyAuthentication notConfigured() {
            return new ApiKeyAuthentication(false, false, Optional.empty());
        }
    }

    private static class CredentialStoreUnavailableException extends RuntimeException {

        CredentialStoreUnavailableException(RuntimeException cause) {
            super(cause);
        }
    }
}

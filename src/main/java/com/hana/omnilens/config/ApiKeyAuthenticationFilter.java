package com.hana.omnilens.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

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

@Component
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER_NAME = "X-HANA-OMNILENS-API-KEY";

    private final OmniLensSecurityProperties properties;
    private final ApiKeyRateLimiter apiKeyRateLimiter;
    private final ApiRequestSignatureVerifier apiRequestSignatureVerifier;

    public ApiKeyAuthenticationFilter(
            OmniLensSecurityProperties properties,
            ApiKeyRateLimiter apiKeyRateLimiter,
            ApiRequestSignatureVerifier apiRequestSignatureVerifier) {
        this.properties = properties;
        this.apiKeyRateLimiter = apiKeyRateLimiter;
        this.apiRequestSignatureVerifier = apiRequestSignatureVerifier;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!properties.apiKeyEnabled() || isPublicEndpoint(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        CachedBodyHttpServletRequest cachedRequest = new CachedBodyHttpServletRequest(request);

        if (!StringUtils.hasText(properties.apiKeySha256())) {
            response.sendError(HttpStatus.SERVICE_UNAVAILABLE.value(), "API key hash is not configured");
            return;
        }

        String providedKey = request.getHeader(HEADER_NAME);
        String providedKeyHash = StringUtils.hasText(providedKey) ? sha256Hex(providedKey) : "";
        if (!StringUtils.hasText(providedKey) || !matchesConfiguredHash(providedKeyHash)) {
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "Invalid API key");
            return;
        }

        RateLimitDecision decision = apiKeyRateLimiter.consume(providedKeyHash);
        if (!decision.allowed()) {
            response.setHeader("Retry-After", String.valueOf(decision.retryAfterSeconds()));
            response.sendError(HttpStatus.TOO_MANY_REQUESTS.value(), "Rate limit exceeded");
            return;
        }

        SignatureVerificationResult signature = apiRequestSignatureVerifier.verify(
                cachedRequest,
                providedKeyHash,
                cachedRequest.body());
        if (!signature.valid()) {
            response.sendError(signature.status().value(), signature.message());
            return;
        }

        filterChain.doFilter(cachedRequest, response);
    }

    private boolean isPublicEndpoint(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator/health") || path.equals("/actuator/info");
    }

    private boolean matchesConfiguredHash(String providedKeyHash) {
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
}

package com.hana.omnilens.portal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.hana.omnilens.common.exception.BusinessException;
import com.hana.omnilens.common.exception.ErrorCode;
import com.hana.omnilens.security.ApiKeyRateLimiter;

@Component
public class PortalAuthenticationRateLimiter {

    private static final int MAX_ATTEMPTS = 10;
    private static final Duration WINDOW = Duration.ofMinutes(10);

    private final ApiKeyRateLimiter rateLimiter;

    public PortalAuthenticationRateLimiter(ApiKeyRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    public Attempt check(String username, HttpServletRequest request) {
        Attempt attempt = new Attempt(
                "portal:ip:" + sha256(clientIp(request)),
                "portal:user:" + sha256(username.trim().toLowerCase(Locale.ROOT)));
        boolean ipAllowed;
        boolean userAllowed;
        try {
            ipAllowed = rateLimiter.consume(attempt.ipKey(), MAX_ATTEMPTS, WINDOW).allowed();
            userAllowed = rateLimiter.consume(attempt.usernameKey(), MAX_ATTEMPTS, WINDOW).allowed();
        } catch (RuntimeException exception) {
            throw new BusinessException(ErrorCode.SECURITY_SERVICE_UNAVAILABLE);
        }
        if (!ipAllowed || !userAllowed) {
            throw new BusinessException(ErrorCode.PORTAL_RATE_LIMITED);
        }
        return attempt;
    }

    public void clear(Attempt attempt) {
        rateLimiter.clear(attempt.ipKey());
        rateLimiter.clear(attempt.usernameKey());
    }

    private String clientIp(HttpServletRequest request) {
        String realIp = request.getHeader("X-Real-IP");
        return StringUtils.hasText(realIp) ? realIp.trim() : request.getRemoteAddr();
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Portal rate-limit key hashing failed", exception);
        }
    }

    public record Attempt(String ipKey, String usernameKey) {
    }
}

package com.hana.omnilens.portal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.hana.omnilens.common.exception.BusinessException;
import com.hana.omnilens.common.exception.ErrorCode;

@Service
public class PortalTokenService {

    private static final long TTL_SECONDS = 60L * 60L * 8L;

    private final PortalProperties properties;

    public PortalTokenService(PortalProperties properties) {
        this.properties = properties;
    }

    public IssuedPortalToken issue(PortalUser user) {
        String key = signingKey();
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plusSeconds(TTL_SECONDS);
        String payload = user.userId() + "." + user.role().name() + "." + expiresAt.getEpochSecond();
        return new IssuedPortalToken(payload + "." + hmac(payload, key), expiresAt);
    }

    public PortalPrincipal verify(String token) {
        if (!StringUtils.hasText(token)) {
            return null;
        }
        String[] parts = token.split("\\.", -1);
        if (parts.length != 4) {
            return null;
        }
        String key;
        try {
            key = signingKey();
        } catch (BusinessException exception) {
            return null;
        }
        String payload = parts[0] + "." + parts[1] + "." + parts[2];
        if (!MessageDigest.isEqual(hmac(payload, key).getBytes(StandardCharsets.UTF_8), parts[3].getBytes(StandardCharsets.UTF_8))) {
            return null;
        }
        try {
            PortalRole role = PortalRole.valueOf(parts[1]);
            if (Instant.now().getEpochSecond() >= Long.parseLong(parts[2])) {
                return null;
            }
            return new PortalPrincipal(parts[0], role);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private String signingKey() {
        if (!StringUtils.hasText(properties.sessionSigningKey())) {
            throw new BusinessException(ErrorCode.PORTAL_SECURITY_NOT_CONFIGURED);
        }
        return properties.sessionSigningKey();
    }

    private String hmac(String value, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Portal token signing failed", exception);
        }
    }

    public record IssuedPortalToken(String value, Instant expiresAt) {
    }

    public record PortalPrincipal(String userId, PortalRole role) {
    }
}

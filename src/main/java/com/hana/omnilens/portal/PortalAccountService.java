package com.hana.omnilens.portal;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import com.hana.omnilens.common.exception.BusinessException;
import com.hana.omnilens.common.exception.ErrorCode;

@Service
public class PortalAccountService {

    private final PortalUserRepository userRepository;
    private final PortalTokenService tokenService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(12);

    public PortalAccountService(PortalUserRepository userRepository, PortalTokenService tokenService) {
        this.userRepository = userRepository;
        this.tokenService = tokenService;
    }

    public PortalSession signUp(String username, String password, String displayName, String phoneNumber) {
        String normalizedUsername = normalizeUsername(username);
        if (userRepository.findByUsername(normalizedUsername).isPresent()) {
            throw new BusinessException(ErrorCode.PORTAL_USER_ALREADY_EXISTS);
        }
        Instant now = Instant.now();
        PortalUser user = new PortalUser(
                "PUSR-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase(Locale.ROOT),
                normalizedUsername,
                passwordEncoder.encode(password),
                displayName.trim(),
                phoneNumber.trim(),
                PortalRole.MEMBER,
                now,
                now);
        try {
            userRepository.save(user);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.PORTAL_USER_ALREADY_EXISTS);
        }
        return session(user);
    }

    public PortalSession login(String username, String password) {
        PortalUser user = userRepository.findByUsername(normalizeUsername(username))
                .orElseThrow(() -> new BusinessException(ErrorCode.PORTAL_INVALID_CREDENTIALS));
        if (!passwordEncoder.matches(password, user.passwordHash())) {
            throw new BusinessException(ErrorCode.PORTAL_INVALID_CREDENTIALS);
        }
        return session(user);
    }

    public void createBootstrapAdminIfConfigured(PortalProperties properties) {
        if (properties.bootstrapAdminUsername() == null || properties.bootstrapAdminUsername().isBlank()
                || properties.bootstrapAdminPassword() == null || properties.bootstrapAdminPassword().isBlank()) {
            return;
        }
        String username = normalizeUsername(properties.bootstrapAdminUsername());
        if (userRepository.findByUsername(username).isPresent()) {
            return;
        }
        Instant now = Instant.now();
        userRepository.save(new PortalUser(
                "PUSR-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase(Locale.ROOT),
                username,
                passwordEncoder.encode(properties.bootstrapAdminPassword()),
                properties.bootstrapAdminName() == null || properties.bootstrapAdminName().isBlank()
                        ? "Hana OmniLens Admin" : properties.bootstrapAdminName().trim(),
                properties.bootstrapAdminPhone() == null ? "" : properties.bootstrapAdminPhone().trim(),
                PortalRole.ADMIN,
                now,
                now));
    }

    private PortalSession session(PortalUser user) {
        PortalTokenService.IssuedPortalToken token = tokenService.issue(user);
        return new PortalSession(user, token.value(), token.expiresAt());
    }

    private String normalizeUsername(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }

    public record PortalSession(PortalUser user, String accessToken, Instant expiresAt) {
    }
}

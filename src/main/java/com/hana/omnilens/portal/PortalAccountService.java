package com.hana.omnilens.portal;

import java.time.Instant;
import java.util.Map;
import java.util.Locale;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.hana.omnilens.common.exception.BusinessException;
import com.hana.omnilens.common.exception.ErrorCode;

@Service
public class PortalAccountService {

    private final PortalUserRepository userRepository;
    private final PortalTokenService tokenService;
    private final PasswordEncoder passwordEncoder;

    public PortalAccountService(PortalUserRepository userRepository, PortalTokenService tokenService) {
        this.userRepository = userRepository;
        this.tokenService = tokenService;
        BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder(12);
        DelegatingPasswordEncoder delegating = new DelegatingPasswordEncoder("bcrypt", Map.of("bcrypt", bcrypt));
        delegating.setDefaultPasswordEncoderForMatches(bcrypt);
        this.passwordEncoder = delegating;
    }

    public PortalSession signUp(String username, String password, String passwordConfirmation, String displayName, String phoneNumber) {
        requireMatchingPasswords(password, passwordConfirmation);
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
                now,
                false,
                0,
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

    public PortalSession changePassword(PortalUser user, String currentPassword, String newPassword, String confirmation) {
        requireMatchingPasswords(newPassword, confirmation);
        if (!passwordEncoder.matches(currentPassword, user.passwordHash())) {
            throw new BusinessException(ErrorCode.PORTAL_INVALID_CREDENTIALS);
        }
        if (passwordEncoder.matches(newPassword, user.passwordHash())) {
            throw new BusinessException(ErrorCode.PORTAL_PASSWORD_REUSE);
        }
        Instant now = Instant.now();
        userRepository.updatePassword(user.userId(), passwordEncoder.encode(newPassword), now);
        PortalUser updated = userRepository.findByUserId(user.userId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PORTAL_AUTHENTICATION_REQUIRED));
        return session(updated);
    }

    private PortalSession session(PortalUser user) {
        PortalTokenService.IssuedPortalToken token = tokenService.issue(user);
        return new PortalSession(user, token.value(), token.expiresAt());
    }

    private String normalizeUsername(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }

    private void requireMatchingPasswords(String password, String confirmation) {
        if (!password.equals(confirmation)) {
            throw new BusinessException(ErrorCode.PORTAL_PASSWORD_MISMATCH);
        }
    }

    public record PortalSession(PortalUser user, String accessToken, Instant expiresAt) {
    }
}

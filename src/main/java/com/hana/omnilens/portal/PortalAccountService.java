package com.hana.omnilens.portal;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.hana.omnilens.common.exception.BusinessException;
import com.hana.omnilens.common.exception.ErrorCode;
import com.hana.omnilens.observability.BusinessEventPublisher;

@Service
public class PortalAccountService {

    private final PortalUserRepository userRepository;
    private final PortalTokenService tokenService;
    private final PasswordEncoder passwordEncoder;
    private final String dummyPasswordHash;
    private final BusinessEventPublisher businessEventPublisher;

    public PortalAccountService(
            PortalUserRepository userRepository,
            PortalTokenService tokenService,
            PasswordEncoder passwordEncoder,
            BusinessEventPublisher businessEventPublisher) {
        this.userRepository = userRepository;
        this.tokenService = tokenService;
        this.passwordEncoder = passwordEncoder;
        this.businessEventPublisher = businessEventPublisher;
        this.dummyPasswordHash = passwordEncoder.encode("dummy-password-not-used");
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
        businessEventPublisher.publish("portal.user.signup", "신규 회원 가입", java.util.Map.of(
                "userId", user.userId(),
                "role", user.role().name()));
        return session(user);
    }

    public PortalSession login(String username, String password) {
        Optional<PortalUser> candidate = userRepository.findByUsername(normalizeUsername(username));
        if (candidate.isEmpty()) {
            passwordEncoder.matches(password, dummyPasswordHash);
            throw new BusinessException(ErrorCode.PORTAL_INVALID_CREDENTIALS);
        }
        PortalUser user = candidate.get();
        if (!passwordEncoder.matches(password, user.passwordHash())) {
            throw new BusinessException(ErrorCode.PORTAL_INVALID_CREDENTIALS);
        }
        if (!user.passwordHash().startsWith("{argon2}")) {
            Instant now = Instant.now();
            userRepository.upgradePasswordHash(user.userId(), passwordEncoder.encode(password), now);
            user = userRepository.findByUserId(user.userId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.PORTAL_INVALID_CREDENTIALS));
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

    public void logout(PortalUser user) {
        userRepository.revokeSessions(user.userId(), Instant.now());
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

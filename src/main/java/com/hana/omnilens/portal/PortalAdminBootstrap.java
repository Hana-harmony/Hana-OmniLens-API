package com.hana.omnilens.portal;

import java.time.Instant;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class PortalAdminBootstrap implements ApplicationRunner {

    static final String ADMIN_USERNAME = "admin";
    static final String LEGACY_PASSWORD_HASH =
            "{bcrypt}$2y$12$QYdm5Z2QBMF/9XgtNMvA5umnErMvlTRskDzg4U5wcIN5PH.X9Sf/K";

    private final PortalUserRepository userRepository;
    private final PortalProperties properties;
    private final PasswordEncoder passwordEncoder;

    public PortalAdminBootstrap(
            PortalUserRepository userRepository,
            PortalProperties properties,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.properties = properties;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        PortalUser admin = userRepository.findByUsername(ADMIN_USERNAME).orElse(null);
        if (admin != null && admin.role() != PortalRole.ADMIN) {
            throw new IllegalStateException("Portal admin username is occupied by a non-admin account");
        }
        if (admin != null && !LEGACY_PASSWORD_HASH.equals(admin.passwordHash())) {
            return;
        }

        String password = requireBootstrapPassword();
        Instant now = Instant.now();
        if (admin == null) {
            userRepository.save(new PortalUser(
                    "PUSR-ADMIN000000000000000",
                    ADMIN_USERNAME,
                    passwordEncoder.encode(password),
                    "Hana OmniLens Admin",
                    "",
                    PortalRole.ADMIN,
                    now,
                    now,
                    true,
                    0,
                    null));
            return;
        }
        userRepository.replaceBootstrapPassword(admin.userId(), passwordEncoder.encode(password), now);
    }

    private String requireBootstrapPassword() {
        String password = properties.bootstrapAdminPassword();
        if (!StringUtils.hasText(password)
                || password.length() < 16
                || password.length() > 128
                || !password.equals(password.trim())) {
            throw new IllegalStateException(
                    "OMNILENS_PORTAL_BOOTSTRAP_ADMIN_PASSWORD must be 16-128 characters without surrounding whitespace");
        }
        return password;
    }
}

package com.hana.omniconnect.portal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

class PortalAdminBootstrapTest {

    private final PasswordEncoder passwordEncoder = new PortalPasswordConfiguration().portalPasswordEncoder();

    @Test
    void replacesLegacyAdminPasswordWithInjectedArgon2Hash() throws Exception {
        PortalUserRepository repository = mock(PortalUserRepository.class);
        PortalUser legacyAdmin = new PortalUser(
                "PUSR-ADMIN000000000000000",
                "admin",
                PortalAdminBootstrap.LEGACY_PASSWORD_HASH,
                "Hana Omni-Connect Admin",
                "",
                PortalRole.ADMIN,
                Instant.EPOCH,
                Instant.EPOCH,
                true,
                0,
                null);
        when(repository.findByUsername("admin")).thenReturn(Optional.of(legacyAdmin));
        PortalProperties properties = new PortalProperties("", "", "Bootstrap-Only-Secret-2026!");

        new PortalAdminBootstrap(repository, properties, passwordEncoder).run(null);

        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        verify(repository).replaceBootstrapPassword(eq(legacyAdmin.userId()), hash.capture(), org.mockito.ArgumentMatchers.any());
        assertThat(hash.getValue()).startsWith("{argon2}");
        assertThat(passwordEncoder.matches(properties.bootstrapAdminPassword(), hash.getValue())).isTrue();
    }

    @Test
    void refusesFreshLegacyAdminWithoutBootstrapSecret() {
        PortalUserRepository repository = mock(PortalUserRepository.class);
        PortalUser legacyAdmin = new PortalUser(
                "PUSR-ADMIN000000000000000",
                "admin",
                PortalAdminBootstrap.LEGACY_PASSWORD_HASH,
                "Hana Omni-Connect Admin",
                "",
                PortalRole.ADMIN,
                Instant.EPOCH,
                Instant.EPOCH,
                true,
                0,
                null);
        when(repository.findByUsername("admin")).thenReturn(Optional.of(legacyAdmin));

        PortalAdminBootstrap bootstrap = new PortalAdminBootstrap(
                repository, new PortalProperties("", "", ""), passwordEncoder);

        assertThatThrownBy(() -> bootstrap.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("OMNI_CONNECT_PORTAL_BOOTSTRAP_ADMIN_PASSWORD");
    }

    @Test
    void existingChangedAdminDoesNotRequireBootstrapSecret() {
        PortalUserRepository repository = mock(PortalUserRepository.class);
        PortalUser admin = new PortalUser(
                "PUSR-ADMIN000000000000000",
                "admin",
                passwordEncoder.encode("Changed-Admin-Password-2026!"),
                "Hana Omni-Connect Admin",
                "",
                PortalRole.ADMIN,
                Instant.EPOCH,
                Instant.EPOCH,
                false,
                1,
                Instant.EPOCH);
        when(repository.findByUsername("admin")).thenReturn(Optional.of(admin));

        new PortalAdminBootstrap(
                repository, new PortalProperties("", "", ""), passwordEncoder).run(null);

        verify(repository).findByUsername("admin");
        verifyNoMoreInteractions(repository);
    }
}

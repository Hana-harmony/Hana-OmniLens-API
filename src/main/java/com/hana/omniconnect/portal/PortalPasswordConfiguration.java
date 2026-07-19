package com.hana.omniconnect.portal;

import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class PortalPasswordConfiguration {

    @Bean
    PasswordEncoder portalPasswordEncoder() {
        BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder(12);
        Argon2PasswordEncoder argon2 = new Argon2PasswordEncoder(16, 32, 1, 19_456, 2);
        DelegatingPasswordEncoder delegating = new DelegatingPasswordEncoder(
                "argon2", Map.of("argon2", argon2, "bcrypt", bcrypt));
        delegating.setDefaultPasswordEncoderForMatches(bcrypt);
        return delegating;
    }
}

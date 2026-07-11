package com.hana.omnilens.portal;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PortalBootstrapAdminConfiguration {

    @Bean
    ApplicationRunner portalBootstrapAdminRunner(PortalAccountService accountService, PortalProperties properties) {
        return arguments -> accountService.createBootstrapAdminIfConfigured(properties);
    }
}

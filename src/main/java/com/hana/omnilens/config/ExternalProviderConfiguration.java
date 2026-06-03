package com.hana.omnilens.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({ExternalProviderProperties.class, HannahAiProperties.class})
public class ExternalProviderConfiguration {
}

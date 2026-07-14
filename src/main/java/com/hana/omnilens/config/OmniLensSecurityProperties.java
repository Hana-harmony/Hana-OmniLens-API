package com.hana.omnilens.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "omnilens.security")
public record OmniLensSecurityProperties(
        String apiKeySha256,
		List<String> apiKeyPartnerIds,
        List<String> corsAllowedOrigins
) {
	public OmniLensSecurityProperties {
		apiKeyPartnerIds = apiKeyPartnerIds == null
				? List.of()
				: apiKeyPartnerIds.stream().filter(id -> id != null && !id.isBlank()).map(String::trim).toList();
	}
}

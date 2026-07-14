package com.hana.omnilens.observability;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "omnilens.observability.discord")
public record DiscordNotificationProperties(String webhookUrl) {
}

package com.hana.omniconnect.observability;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "omni-connect.observability.discord")
public record DiscordNotificationProperties(String webhookUrl) {
}

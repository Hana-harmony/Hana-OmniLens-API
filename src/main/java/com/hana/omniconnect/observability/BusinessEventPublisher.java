package com.hana.omniconnect.observability;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.env.Environment;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.micrometer.core.instrument.MeterRegistry;

@Component
public class BusinessEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(BusinessEventPublisher.class);
    private static final int MAX_FIELD_LENGTH = 500;
    private static final int MAX_ATTEMPTS = 5;

    private final DiscordNotificationProperties properties;
    private final ObjectMapper objectMapper;
    private final TaskExecutor executor;
    private final MeterRegistry meterRegistry;
    private final Environment environment;
    private final HttpClient httpClient;
    private URI webhookUri;

    public BusinessEventPublisher(
            DiscordNotificationProperties properties,
            ObjectMapper objectMapper,
            @Qualifier("discordNotificationExecutor") TaskExecutor executor,
            MeterRegistry meterRegistry,
            Environment environment) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.executor = executor;
        this.meterRegistry = meterRegistry;
        this.environment = environment;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @PostConstruct
    void validateConfiguration() {
        String configuredUrl = properties.webhookUrl();
        if (configuredUrl == null || configuredUrl.isBlank()) {
            if (Arrays.asList(environment.getActiveProfiles()).contains("prod")) {
                throw new IllegalStateException("OMNI_CONNECT_DISCORD_WEBHOOK_URL is required in production");
            }
            log.info("Discord business notifications are waiting for OMNI_CONNECT_DISCORD_WEBHOOK_URL");
            return;
        }
        URI candidate = URI.create(configuredUrl);
        if (!"https".equals(candidate.getScheme())
                || !"discord.com".equals(candidate.getHost())
                || candidate.getPath() == null
                || !candidate.getPath().startsWith("/api/webhooks/")) {
            throw new IllegalStateException("OMNI_CONNECT_DISCORD_WEBHOOK_URL must be an HTTPS Discord webhook URL");
        }
        webhookUri = candidate;
    }

    public void publish(String type, String title, Map<String, ?> details) {
        BusinessEvent event = new BusinessEvent(
                UUID.randomUUID().toString(), type, title, safeDetails(details), Instant.now());
        Runnable dispatch = () -> submit(event);
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatch.run();
                }
            });
            return;
        }
        dispatch.run();
    }

    private void submit(BusinessEvent event) {
        meterRegistry.counter("business_events_total", "service", "omni-connect", "type", event.type()).increment();
        if (webhookUri == null) {
            meterRegistry.counter("discord_notifications_total", "service", "omni-connect", "result", "not_configured")
                    .increment();
            return;
        }
        try {
            executor.execute(() -> send(event));
        } catch (RejectedExecutionException exception) {
            meterRegistry.counter("discord_notifications_total", "service", "omni-connect", "result", "queue_rejected")
                    .increment();
            log.error("Discord business notification queue rejected event type={} eventId={}",
                    event.type(), event.eventId(), exception);
        }
    }

    private void send(BusinessEvent event) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(payload(event));
        } catch (JsonProcessingException exception) {
            recordFailure(event, "serialization_failed", exception);
            return;
        }
        Exception lastFailure = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder(webhookUri)
                        .timeout(Duration.ofSeconds(5))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(payload))
                        .build();
                HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    meterRegistry.counter("discord_notifications_total", "service", "omni-connect", "result", "sent")
                            .increment();
                    return;
                }
                lastFailure = new IllegalStateException("Discord returned HTTP " + response.statusCode());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                recordFailure(event, "interrupted", exception);
                return;
            } catch (Exception exception) {
                lastFailure = exception;
            }
            if (attempt < MAX_ATTEMPTS) {
                try {
                    Thread.sleep(1_000L << (attempt - 1));
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    recordFailure(event, "interrupted", exception);
                    return;
                }
            }
        }
        recordFailure(event, "send_failed", lastFailure);
    }

    private Map<String, Object> payload(BusinessEvent event) {
        List<Map<String, Object>> fields = event.details().entrySet().stream()
                .map(entry -> {
                    Map<String, Object> field = new LinkedHashMap<>();
                    field.put("name", truncate(entry.getKey()));
                    field.put("value", truncate(entry.getValue()));
                    field.put("inline", true);
                    return field;
                })
                .toList();
        Map<String, Object> embed = new LinkedHashMap<>();
        embed.put("title", truncate(event.title()));
        embed.put("description", "event=" + event.type() + " id=" + event.eventId());
        embed.put("color", 0x008485);
        embed.put("timestamp", event.occurredAt().toString());
        embed.put("fields", fields);
        return Map.of(
                "username", "Hana Omni-Connect",
                "allowed_mentions", Map.of("parse", List.of()),
                "embeds", List.of(embed));
    }

    private Map<String, String> safeDetails(Map<String, ?> details) {
        Map<String, String> safe = new LinkedHashMap<>();
        details.forEach((key, value) -> safe.put(truncate(key), truncate(String.valueOf(value))));
        return Map.copyOf(safe);
    }

    private String truncate(String value) {
        return value.length() <= MAX_FIELD_LENGTH ? value : value.substring(0, MAX_FIELD_LENGTH);
    }

    private void recordFailure(BusinessEvent event, String result, Exception exception) {
        meterRegistry.counter("discord_notifications_total", "service", "omni-connect", "result", result).increment();
        log.error("Discord business notification failed type={} eventId={}", event.type(), event.eventId(), exception);
    }

    private record BusinessEvent(
            String eventId,
            String type,
            String title,
            Map<String, String> details,
            Instant occurredAt) {
    }
}

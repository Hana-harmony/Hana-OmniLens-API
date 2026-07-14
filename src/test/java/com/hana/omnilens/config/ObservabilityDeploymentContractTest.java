package com.hana.omnilens.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class ObservabilityDeploymentContractTest {

    @Test
    void monitoringIsPrivatePersistentAndRoutesAlertsByService() throws IOException {
        String compose = read("deploy/monitoring/compose.yml");
        String contacts = read("deploy/monitoring/grafana/provisioning/alerting/contact-points.yml");
        String workflow = read(".github/workflows/ci.yml");
        String nginx = read("deploy/nginx/hana-omnilens-api.conf");

        assertThat(compose).contains("127.0.0.1:${GRAFANA_HOST_PORT:-3300}:3000");
        assertThat(compose).contains("prometheus-data", "loki-data", "grafana-data");
        assertThat(compose).doesNotContain("discord.com/api/webhooks/");
        assertThat(contacts).contains("$OMNILENS_DISCORD_WEBHOOK_URL", "$HANNAH_DISCORD_WEBHOOK_URL");
        assertThat(workflow).contains(
                "secrets.OMNILENS_DISCORD_WEBHOOK_URL",
                "secrets.HANNAH_DISCORD_WEBHOOK_URL",
                "secrets.GRAFANA_ADMIN_PASSWORD");
        assertThat(nginx).contains("location = /actuator/prometheus { return 404; }");
    }

    private static String read(String path) throws IOException {
        return Files.readString(Path.of(path));
    }
}

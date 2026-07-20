package com.hana.omniconnect.config;

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
        String nginx = read("deploy/nginx/hana-omni-connect-api.conf");

        assertThat(compose).contains("127.0.0.1:${GRAFANA_HOST_PORT:-3300}:3000");
        assertThat(compose).contains(
                "GF_SERVER_ROOT_URL: https://api.hanaomni.cloud/grafana/",
                "GF_SERVER_SERVE_FROM_SUB_PATH: \"true\"",
                "GF_SECURITY_COOKIE_SECURE: \"true\"");
        assertThat(compose).contains("prometheus-data", "loki-data", "grafana-data");
        assertThat(compose).doesNotContain("discord.com/api/webhooks/");
        assertThat(contacts).contains("$OMNI_CONNECT_DISCORD_WEBHOOK_URL", "$HANNAH_DISCORD_WEBHOOK_URL");
        assertThat(workflow).contains(
                "secrets.OMNI_CONNECT_DISCORD_WEBHOOK_URL",
                "secrets.HANNAH_DISCORD_WEBHOOK_URL",
                "secrets.GRAFANA_ADMIN_PASSWORD");
        assertThat(nginx).contains("location = /actuator/prometheus { return 404; }");
        assertThat(nginx).contains("location /grafana/", "proxy_pass http://127.0.0.1:3300;");
    }

    private static String read(String path) throws IOException {
        return Files.readString(Path.of(path));
    }
}

package com.hana.omnilens.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class DeploymentProfileGuardrailTest {

    private static final List<String> REQUIRED_PROD_PLACEHOLDERS =
            List.of(
                    "OMNILENS_API_KEY_SHA256",
                    "OMNILENS_CORS_ALLOWED_ORIGINS",
                    "DB_URL",
                    "DB_USERNAME",
                    "DB_PASSWORD",
                    "REDIS_HOST",
                    "PUBLIC_DATA_SERVICE_KEY",
                    "NAVER_NEWS_CLIENT_ID",
                    "NAVER_NEWS_CLIENT_SECRET",
                    "OPEN_DART_API_KEY",
                    "KRX_ID",
                    "KRX_PW",
                    "KRX_OPEN_API_AUTH_KEY",
                    "KIS_ACCOUNT_NUMBER",
                    "KIS_APP_KEY",
                    "KIS_APP_SECRET",
                    "KIS_REAL_ACCOUNT_NUMBER",
                    "KIS_REAL_APP_KEY",
                    "KIS_REAL_APP_SECRET");

    @Test
    void localRuntimeFilesStayIgnoredAndProdProfileIsTracked() throws IOException {
        String gitignore = read(".gitignore");

        assertThat(gitignore).contains("src/main/resources/application-local.yml");
        assertThat(gitignore).contains("/application-prod.env");
        assertThat(gitignore).contains("/deploy-prod.env");
        assertThat(Path.of("src/main/resources/application-prod.yml")).exists();
        assertThat(Path.of("src/main/resources/application-local.example.yml")).exists();
    }

    @Test
    void prodProfileUsesEnvironmentPlaceholdersForRequiredSecrets() throws IOException {
        String prodProfile = read("src/main/resources/application-prod.yml");

        for (String placeholder : REQUIRED_PROD_PLACEHOLDERS) {
            assertThat(prodProfile).contains("${" + placeholder + "}");
            assertThat(prodProfile).doesNotContain("${" + placeholder + ":");
        }
        assertThat(prodProfile).doesNotContain("replace-with-");
        assertThat(prodProfile).contains("base-url: ${HANNAH_AI_BASE_URL:http://hannah-montana-ai:8000}");
    }

    @Test
    void prodDeploymentUsesHardenedBlueGreenContainers() throws IOException {
        String deployScript = read("scripts/deploy-prod.sh");

        assertThat(deployScript).contains("active-slot");
        assertThat(deployScript).contains("inactive=green");
        assertThat(deployScript).contains("inactive=blue");
        assertThat(deployScript).contains("--read-only");
        assertThat(deployScript).contains("--cap-drop ALL");
        assertThat(deployScript).contains("--security-opt no-new-privileges:true");
        assertThat(deployScript).contains("--env-file \"${APP_DIR}/application.env\"");
        assertThat(deployScript).contains("--spring.profiles.active=prod");
        assertThat(deployScript).doesNotContain("application-local.yml");
    }

    @Test
    void localComposeBindsHannahAiUrlToSpringConfigurationProperty() throws IOException {
        String compose = read("compose.local.yml");

        assertThat(compose).contains("OMNILENS_AI_HANNAH_BASE_URL: http://host.docker.internal:8000");
        assertThat(compose).contains("SPRING_CONFIG_ADDITIONAL_LOCATION: file:/app/config/application-local.yml");
        assertThat(compose).contains("./src/main/resources/application-local.yml:/app/config/application-local.yml:ro");
        assertThat(compose).doesNotContain("PROVIDERS_AI_HANNAH_BASE_URL");
    }

    @Test
    void githubActionsDeploysProdOnlyFromMainThroughGhcr() throws IOException {
        String workflow = read(".github/workflows/ci.yml");

        assertThat(workflow).contains("github.ref == 'refs/heads/main'");
        assertThat(workflow).contains("environment: production");
        assertThat(workflow).contains("registry: ghcr.io");
        assertThat(workflow).contains("docker/build-push-action");
        assertThat(workflow).contains("push: true");
        assertThat(workflow).contains("application.env");
        assertThat(workflow).contains("deploy.env");
        assertThat(workflow).contains("platforms: linux/arm64");
        assertThat(workflow).contains("PROD_HOST_KEY");
        assertThat(workflow).contains("scripts/bootstrap-https.sh");
        assertThat(workflow).doesNotContain("GHCR_USERNAME");
    }

    @Test
    void remoteDeployScriptUsesRequiredEnvAndAtomicNginxSwitch() throws IOException {
        String deployScript = read("scripts/deploy-prod.sh");

        assertThat(deployScript).contains("APP_DIR=/opt/hana-omnilens-api");
        assertThat(deployScript).contains("source \"${APP_DIR}/deploy.env\"");
        assertThat(deployScript).contains("docker login ghcr.io");
        assertThat(deployScript).contains("--env-file \"${APP_DIR}/application.env\"");
        assertThat(deployScript).contains("sudo nginx -t");
        assertThat(deployScript).contains("sudo systemctl reload nginx");
        assertThat(deployScript).contains("https://api.hanaomilens.cloud/actuator/health");
        assertThat(deployScript).doesNotContain("application-local.yml");
    }

    private static String read(String path) throws IOException {
        return Files.readString(Path.of(path));
    }
}

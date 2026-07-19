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
        assertThat(gitignore).contains("/application.env");
        assertThat(gitignore).contains("/deploy.env");
        assertThat(gitignore).contains("/postgres-password");
        assertThat(gitignore).contains("/redis-users.acl");
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
        assertThat(prodProfile).contains(
                "bootstrap-admin-password: ${OMNILENS_PORTAL_BOOTSTRAP_ADMIN_PASSWORD:}");
    }

    @Test
    void prodDeploymentUsesHardenedSingleContainerWithRollback() throws IOException {
        String deployScript = read("scripts/deploy-prod.sh");

        assertThat(deployScript).contains("previous_image");
        assertThat(deployScript).contains("rollback");
        assertThat(deployScript).doesNotContain("active-slot");
        assertThat(deployScript).doesNotContain("inactive=green");
        assertThat(deployScript).doesNotContain("inactive=blue");
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

        assertThat(compose).contains("OMNILENS_AI_HANNAH_BASE_URL: http://hannah-montana-ai:8000");
        assertThat(compose).contains("name: hana-omnilens-internal");
        assertThat(compose).doesNotContain("host.docker.internal");
        assertThat(compose).doesNotContain("OMNILENS_MARKET_HISTORY_COLLECTION_ENABLED: \"false\"");
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
        assertThat(workflow).doesNotContain("secrets.OMNILENS_API_KEY_SHA256");
        assertThat(workflow).contains("if [[ -n \"${PORTAL_BOOTSTRAP_PASSWORD}\" ]]");
        assertThat(workflow).contains("push: true");
        assertThat(workflow).contains("application.env");
        assertThat(workflow).contains("deploy.env");
        assertThat(workflow).contains("platforms: linux/arm64");
        assertThat(workflow).contains("PROD_HOST_KEY");
        assertThat(workflow).contains(
                "secrets.PROD_SSH_PASSWORD",
                "SSH_ASKPASS",
                "PreferredAuthentications publickey,password",
                "StrictHostKeyChecking yes",
                "scripts/ssh-askpass.sh");
        assertThat(workflow).contains("GHCR_USERNAME", "https://api.github.com/user", "secrets.GHCR_TOKEN");
        assertThat(workflow).doesNotContain("secrets.GHCR_USERNAME");
        assertThat(workflow).contains("scripts/bootstrap-https.sh");
        assertThat(workflow).doesNotContain("secrets.DB_URL");
        assertThat(workflow).doesNotContain("secrets.DB_USERNAME");
        assertThat(workflow).doesNotContain("secrets.REDIS_HOST");
        assertThat(workflow).doesNotContain("secrets.REDIS_PORT");
        assertThat(workflow).doesNotContain(
                "secrets.OMNILENS_PORTAL_SESSION_SIGNING_KEY",
                "secrets.OMNILENS_PORTAL_API_KEY_ENCRYPTION_KEY",
                "secrets.OMNILENS_TERM_ANALYTICS_HASH_SALT",
                "secrets.HANNAH_AI_MAINTENANCE_TOKEN");
        assertThat(workflow).contains("jdbc:postgresql://hana-omnilens-postgres:5432/omnilens");
        assertThat(workflow).contains("REDIS_HOST=hana-omnilens-redis");
        assertThat(workflow).contains("HANNAH_AI_BASE_URL=http://hannah-montana-ai:8000");
        assertThat(workflow).doesNotContain("HANNAH_AI_BASE_URL=http://host.docker.internal");
    }

    @Test
    void prodPostgresAndRedisUsePrivatePersistentContainers() throws IOException {
        String compose = read("deploy/compose/hana-omnilens-data.yml");
        String bootstrap = read("scripts/bootstrap-data-services.sh");

        assertThat(compose).contains("postgres:17.10-bookworm@sha256:");
        assertThat(compose).contains("redis:8.2.7-bookworm@sha256:");
        assertThat(compose).contains("POSTGRES_INITDB_ARGS: --auth-host=scram-sha-256 --data-checksums");
        assertThat(compose).contains("--appendonly");
        assertThat(compose).contains("noeviction");
        assertThat(compose).contains("--user omnilens_app");
        assertThat(compose).contains("name: hana-omnilens-postgres-data");
        assertThat(compose).contains("name: hana-omnilens-redis-data");
        assertThat(compose).contains("name: hana-omnilens-internal");
        assertThat(compose).contains("external: true");
        assertThat(compose).doesNotContain("ports:");
        assertThat(bootstrap).contains("docker compose");
        assertThat(bootstrap).contains("State.Health.Status");

        String prodConfig = read("src/main/resources/application-prod.yml");
        assertThat(prodConfig).contains("username: omnilens_app");
        assertThat(workflow()).contains("user default off");
        assertThat(workflow()).contains("~omnilens:*");
    }

    private String workflow() throws IOException {
        return read(".github/workflows/ci.yml");
    }

    @Test
    void remoteDeployScriptUsesRequiredEnvAndAtomicNginxSwitch() throws IOException {
        String deployScript = read("scripts/deploy-prod.sh");

        assertThat(deployScript).contains("APP_DIR=/opt/hana-omnilens-api");
        assertThat(deployScript).contains("source \"${APP_DIR}/deploy.env\"");
        assertThat(deployScript).contains("docker login ghcr.io");
        assertThat(deployScript).contains("GHCR_USERNAME");
        assertThat(deployScript).contains("--env-file \"${APP_DIR}/application.env\"");
        assertThat(deployScript).contains("--env-file \"${RUNTIME_APP_ENV}\"");
        assertThat(deployScript).contains("NETWORK=hana-omnilens-internal");
        assertThat(deployScript).contains("--network \"${NETWORK}\"");
        assertThat(deployScript).contains("sudo nginx -t");
        assertThat(deployScript).contains("sudo systemctl reload nginx");
        assertThat(deployScript).contains("https://api.hanaomni.cloud/actuator/health");
        assertThat(deployScript).doesNotContain("application-local.yml");
    }

    @Test
    void runtimeKeysAreDerivedFromThePersistentOciHostRoot() throws IOException {
        String workflow = read(".github/workflows/ci.yml");
        String bootstrap = read("scripts/bootstrap-host.sh");
        String runtimeSecrets = read("scripts/runtime-secrets.sh");
        String deploy = read("scripts/deploy-prod.sh");

        assertThat(workflow).contains("scripts/runtime-secrets.sh");
        assertThat(bootstrap).contains("ensure_runtime_root_secret");
        assertThat(runtimeSecrets).contains(
                "HANA_RUNTIME_SECRET_DIR=/opt/hana-runtime",
                "root-secret",
                "openssl rand -hex 32",
                "flock -x",
                "derive_runtime_secret_hex",
                "derive_runtime_secret_base64");
        assertThat(deploy).contains(
                "hana/omnilens/portal-session-signing/v1",
                "hana/omnilens/portal-api-key-encryption/v1",
                "hana/omnilens/term-analytics-hash/v1",
                "hana/ai/maintenance-auth/v1",
                "runtime-application.env");
    }

    @Test
    void productionDomainsStayAlignedAcrossDeploymentSurfaces() throws IOException {
        String deployment = String.join(
                "\n",
                read(".github/workflows/ci.yml"),
                read("scripts/bootstrap-https.sh"),
                read("scripts/deploy-prod.sh"),
                read("deploy/nginx/bootstrap-http.conf"),
                read("deploy/nginx/hana-omnilens-api.conf"),
                read("deploy/monitoring/prometheus.yml"));

        assertThat(deployment).contains("https://hanaomni.cloud");
        assertThat(deployment).contains("api.hanaomni.cloud");
        assertThat(deployment).doesNotContain("hanaomilens.cloud");
        assertThat(deployment).doesNotContain("hanaomnilens.cloud");
    }

    private static String read(String path) throws IOException {
        return Files.readString(Path.of(path));
    }
}

package com.hana.omniconnect.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class DeploymentProfileGuardrailTest {

    private static final List<String> REQUIRED_PROD_PLACEHOLDERS =
            List.of(
                    "OMNI_CONNECT_CORS_ALLOWED_ORIGINS",
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
                "bootstrap-admin-password: ${OMNI_CONNECT_PORTAL_BOOTSTRAP_ADMIN_PASSWORD:}");
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

        assertThat(compose).contains("OMNI_CONNECT_AI_HANNAH_BASE_URL: http://hannah-montana-ai:8000");
        assertThat(compose).contains("name: hana-omni-connect-internal");
        assertThat(compose).doesNotContain("host.docker.internal");
        assertThat(compose).doesNotContain("OMNI_CONNECT_MARKET_HISTORY_COLLECTION_ENABLED: \"false\"");
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
        assertThat(workflow).doesNotContain("secrets.OMNI_CONNECT_API_KEY_SHA256");
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
        assertThat(workflow).doesNotContain("ControlMaster", "ControlPersist");
        assertThat(workflow).contains("GHCR_USERNAME", "https://api.github.com/user", "secrets.GHCR_TOKEN");
        assertThat(workflow).doesNotContain("secrets.GHCR_USERNAME");
        assertThat(workflow).contains("scripts/bootstrap-https.sh");
        assertThat(workflow).contains("scripts/ensure-web-ingress.sh");
        assertThat(workflow).contains("hana-omni-connect-web-ingress.service");
        assertThat(workflow).doesNotContain("secrets.DB_URL");
        assertThat(workflow).doesNotContain("secrets.DB_USERNAME");
        assertThat(workflow).doesNotContain("secrets.REDIS_HOST");
        assertThat(workflow).doesNotContain("secrets.REDIS_PORT");
        assertThat(workflow).doesNotContain(
                "secrets.OMNI_CONNECT_PORTAL_SESSION_SIGNING_KEY",
                "secrets.OMNI_CONNECT_PORTAL_API_KEY_ENCRYPTION_KEY",
                "secrets.OMNI_CONNECT_TERM_ANALYTICS_HASH_SALT",
                "secrets.HANNAH_AI_MAINTENANCE_TOKEN");
        assertThat(workflow).contains("jdbc:postgresql://hana-omni-connect-postgres:5432/omni-connect");
        assertThat(workflow).contains("REDIS_HOST=hana-omni-connect-redis");
        assertThat(workflow).contains("HANNAH_AI_BASE_URL=http://hannah-montana-ai:8000");
        assertThat(workflow).doesNotContain("HANNAH_AI_BASE_URL=http://host.docker.internal");
    }

    @Test
    void prodPostgresAndRedisUsePrivatePersistentContainers() throws IOException {
        String compose = read("deploy/compose/hana-omni-connect-data.yml");
        String bootstrap = read("scripts/bootstrap-data-services.sh");

        assertThat(compose).contains("postgres:17.10-bookworm@sha256:");
        assertThat(compose).contains("redis:8.2.7-bookworm@sha256:");
        assertThat(compose).contains("POSTGRES_INITDB_ARGS: --auth-host=scram-sha-256 --data-checksums");
        assertThat(compose).contains("--appendonly");
        assertThat(compose).contains("noeviction");
        assertThat(compose).contains("--user omni_connect_app");
        assertThat(compose).contains("name: hana-omni-connect-postgres-data");
        assertThat(compose).contains("name: hana-omni-connect-redis-data");
        assertThat(compose).contains("name: hana-omni-connect-internal");
        assertThat(compose).contains("external: true");
        assertThat(compose).doesNotContain("ports:");
        assertThat(bootstrap).contains("docker compose");
        assertThat(bootstrap).contains("State.Health.Status");
        assertThat(bootstrap).contains("chmod 600 \"${APP_DIR}/postgres-password\"");
        assertThat(bootstrap).contains("sudo chgrp 999 \"${APP_DIR}/redis-users.acl\"");
        assertThat(bootstrap).contains("chmod 640 \"${APP_DIR}/redis-users.acl\"");
        assertThat(bootstrap).contains("stat -c '%g:%a'");
        assertThat(bootstrap)
                .doesNotContain(
                        "chmod 600 \"${APP_DIR}/postgres-password\" \"${APP_DIR}/redis-users.acl\"");

        String prodConfig = read("src/main/resources/application-prod.yml");
        assertThat(prodConfig).contains("username: omni_connect_app");
        assertThat(workflow()).contains("user default off");
        assertThat(workflow()).contains("~omni-connect:*");
    }

    private String workflow() throws IOException {
        return read(".github/workflows/ci.yml");
    }

    @Test
    void remoteDeployScriptUsesRequiredEnvAndAtomicNginxSwitch() throws IOException {
        String deployScript = read("scripts/deploy-prod.sh");

        assertThat(deployScript).contains("APP_DIR=/opt/hana-omni-connect-api");
        assertThat(deployScript).contains("source \"${APP_DIR}/deploy.env\"");
        assertThat(deployScript).contains("docker login ghcr.io");
        assertThat(deployScript).contains("GHCR_USERNAME");
        assertThat(deployScript).contains("--env-file \"${APP_DIR}/application.env\"");
        assertThat(deployScript).contains("--env-file \"${RUNTIME_APP_ENV}\"");
        assertThat(deployScript).contains("NETWORK=hana-omni-connect-internal");
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
                "hana/omni-connect/portal-session-signing/v1",
                "hana/omni-connect/portal-api-key-encryption/v1",
                "hana/omni-connect/term-analytics-hash/v1",
                "hana/ai/maintenance-auth/v1",
                "runtime-application.env");
    }

    @Test
    void hostBootstrapKeepsPublicWebIngressAcrossReboots() throws IOException {
        String bootstrap = read("scripts/bootstrap-host.sh");
        String ingressScript = read("scripts/ensure-web-ingress.sh");
        String ingressService = read("deploy/systemd/hana-omni-connect-web-ingress.service");

        assertThat(bootstrap).contains("hana-omni-connect-web-ingress.service");
        assertThat(bootstrap).contains("enable --now hana-omni-connect-web-ingress.service");
        assertThat(ingressScript).contains("--dport \"${port}\"");
        assertThat(ingressScript).contains("for port in 80 443");
        assertThat(ingressService).contains("After=network-online.target cloud-final.service");
        assertThat(ingressService).contains("WantedBy=multi-user.target");
    }

    @Test
    void productionDomainsStayAlignedAcrossDeploymentSurfaces() throws IOException {
        String deployment = String.join(
                "\n",
                read(".github/workflows/ci.yml"),
                read("scripts/bootstrap-https.sh"),
                read("scripts/deploy-prod.sh"),
                read("deploy/nginx/bootstrap-http.conf"),
                read("deploy/nginx/hana-omni-connect-api.conf"),
                read("deploy/monitoring/prometheus.yml"));

        assertThat(deployment).contains("https://hanaomni.cloud");
        assertThat(deployment).contains("api.hanaomni.cloud");
        assertThat(deployment).doesNotContain("hanaomilens.cloud");
        assertThat(deployment).doesNotContain("hanaomniconnect.cloud");
    }

    private static String read(String path) throws IOException {
        return Files.readString(Path.of(path));
    }
}

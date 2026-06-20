package com.hana.omnilens.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.hana.omnilens.alert.application.InMemoryAlertDedupeStore;
import com.hana.omnilens.alert.application.RedisAlertDedupeStore;
import com.hana.omnilens.market.application.InMemoryExchangeRateCache;
import com.hana.omnilens.market.application.InMemoryForeignOwnershipSnapshotCache;
import com.hana.omnilens.market.application.RedisExchangeRateCache;
import com.hana.omnilens.market.application.RedisForeignOwnershipSnapshotCache;
import com.hana.omnilens.provider.market.ForeignOwnershipSnapshot;
import com.hana.omnilens.security.RedisApiSignatureNonceStore;

@Testcontainers(disabledWithoutDocker = true)
class RedisIntegrationTest {

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379)
            .waitingFor(Wait.forListeningPort());

    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().findAndAddModules().build();

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;

    @BeforeAll
    static void connectRedis() {
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(
                REDIS.getHost(),
                REDIS.getMappedPort(6379));
        connectionFactory = new LettuceConnectionFactory(configuration);
        connectionFactory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
    }

    @AfterAll
    static void closeRedisConnection() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    void redisBackedSecurityAndMarketStoresWorkAgainstRealRedis() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();

        RedisApiSignatureNonceStore nonceStore = new RedisApiSignatureNonceStore(redisTemplate);
        Instant expiresAt = Instant.now().plus(Duration.ofMinutes(5));

        assertThat(nonceStore.remember("fingerprint-a", "nonce-a", expiresAt)).isTrue();
        assertThat(nonceStore.remember("fingerprint-a", "nonce-a", expiresAt)).isFalse();
        assertThat(redisTemplate.getExpire(
                "omnilens:security:signature:nonce:fingerprint-a:nonce-a",
                TimeUnit.SECONDS)).isPositive();

        RedisAlertDedupeStore dedupeStore = new RedisAlertDedupeStore(
                redisTemplate,
                new InMemoryAlertDedupeStore(1_000),
                Duration.ofHours(1));

        assertThat(dedupeStore.markIfFirst("alert-a")).isTrue();
        assertThat(dedupeStore.markIfFirst("alert-a")).isFalse();
        dedupeStore.remove("alert-a");
        assertThat(dedupeStore.markIfFirst("alert-a")).isTrue();

        RedisExchangeRateCache exchangeRateCache = new RedisExchangeRateCache(
                redisTemplate,
                OBJECT_MAPPER,
                new InMemoryExchangeRateCache(),
                Duration.ofHours(24));

        exchangeRateCache.put("usd", new BigDecimal("0.00074"), Instant.parse("2026-06-20T00:00:00Z"));
        assertThat(exchangeRateCache.find("USD")).isPresent();
        assertThat(exchangeRateCache.find("USD").orElseThrow().fxRate()).isEqualByComparingTo("0.00074");

        RedisForeignOwnershipSnapshotCache foreignOwnershipCache = new RedisForeignOwnershipSnapshotCache(
                redisTemplate,
                OBJECT_MAPPER,
                new InMemoryForeignOwnershipSnapshotCache(),
                Duration.ofHours(24));

        foreignOwnershipCache.put(new ForeignOwnershipSnapshot(
                "005930",
                3_642_091_300L,
                new BigDecimal("54.19"),
                6_720_000_000L,
                new BigDecimal("54.21"),
                LocalDate.of(2026, 6, 19)));

        assertThat(foreignOwnershipCache.find("005930")).isPresent();
        assertThat(foreignOwnershipCache.find("005930").orElseThrow().foreignLimitExhaustionRate())
                .isEqualByComparingTo("54.21");
    }
}

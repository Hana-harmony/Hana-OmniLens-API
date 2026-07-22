package com.hana.omniconnect.alert.infra;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.hana.omniconnect.alert.application.DisclosureProcessingJob;
import com.hana.omniconnect.alert.application.DisclosureProcessingRepository;

@SpringBootTest(properties = {
        "omni-connect.alert.dedupe.mode=in-memory",
        "omni-connect.market.exchange-rate-cache.mode=in-memory",
        "omni-connect.market.foreign-ownership-cache.mode=in-memory"
})
class JdbcDisclosureProcessingRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-07-22T10:00:00Z");

    @Autowired
    private DisclosureProcessingRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void deleteJobs() {
        jdbcTemplate.update("DELETE FROM disclosure_processing_job");
    }

    @Test
    void enqueueIsIdempotentAndRetryRemainsClaimable() {
        DisclosureProcessingJob pending = pendingJob();

        assertThat(repository.enqueue(pending, NOW)).isTrue();
        assertThat(repository.enqueue(pending, NOW.plusSeconds(1))).isFalse();

        DisclosureProcessingJob firstClaim = repository
                .claimNext(NOW, Duration.ofMinutes(45))
                .orElseThrow();
        assertThat(firstClaim.status()).isEqualTo("PROCESSING");
        assertThat(firstClaim.attemptCount()).isEqualTo(1);
        assertThat(firstClaim.leaseToken()).isNotBlank();

        repository.scheduleRetry(
                firstClaim,
                "Qwen unavailable",
                NOW.plus(Duration.ofMinutes(1)),
                NOW);

        assertThat(repository.claimNext(NOW.plusSeconds(30), Duration.ofMinutes(45))).isEmpty();
        DisclosureProcessingJob secondClaim = repository
                .claimNext(NOW.plus(Duration.ofMinutes(1)), Duration.ofMinutes(45))
                .orElseThrow();
        assertThat(secondClaim.attemptCount()).isEqualTo(2);
    }

    private DisclosureProcessingJob pendingJob() {
        return new DisclosureProcessingJob(
                "job-1",
                "omni-connect-default-universe",
                "005930",
                "20260722000001",
                "삼성전자",
                "주요사항보고서",
                "https://dart.fss.or.kr/dsaf001/main.do?rcpNo=20260722000001",
                NOW.minus(Duration.ofHours(1)),
                null,
                null,
                null,
                "PENDING",
                0,
                null);
    }
}

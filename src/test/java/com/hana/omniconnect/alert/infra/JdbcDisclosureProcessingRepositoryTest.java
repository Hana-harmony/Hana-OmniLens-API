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

    @Test
    void claimNextRotatesAcrossStocksBeforeTakingAnotherJobFromSameStock() {
        repository.enqueue(pendingJob("job-a1", "005930", NOW.minusSeconds(10)), NOW);
        repository.enqueue(pendingJob("job-a2", "005930", NOW.minusSeconds(20)), NOW.plusSeconds(1));
        repository.enqueue(pendingJob("job-b1", "030200", NOW.minusSeconds(30)), NOW.plusSeconds(2));

        DisclosureProcessingJob first = repository.claimNext(NOW.plusSeconds(3), Duration.ofMinutes(45))
                .orElseThrow();
        DisclosureProcessingJob second = repository.claimNext(NOW.plusSeconds(3), Duration.ofMinutes(45))
                .orElseThrow();

        assertThat(first.stockCode()).isEqualTo("005930");
        assertThat(second.stockCode()).isEqualTo("030200");
    }

    private DisclosureProcessingJob pendingJob() {
        return pendingJob("job-1", "005930", NOW.minus(Duration.ofHours(1)));
    }

    private DisclosureProcessingJob pendingJob(String jobId, String stockCode, Instant publishedAt) {
        return new DisclosureProcessingJob(
                jobId,
                "omni-connect-default-universe",
                stockCode,
                "20260722" + jobId,
                stockCode,
                "주요사항보고서",
                "https://dart.fss.or.kr/dsaf001/main.do?rcpNo=" + jobId,
                publishedAt,
                null,
                null,
                null,
                "PENDING",
                0,
                null);
    }
}

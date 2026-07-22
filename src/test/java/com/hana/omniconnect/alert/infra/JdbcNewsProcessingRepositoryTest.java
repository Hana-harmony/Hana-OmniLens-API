package com.hana.omniconnect.alert.infra;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.hana.omniconnect.alert.application.NewsProcessingJob;
import com.hana.omniconnect.alert.application.NewsProcessingRepository;

@SpringBootTest(properties = {
        "omni-connect.alert.dedupe.mode=in-memory",
        "omni-connect.market.exchange-rate-cache.mode=in-memory",
        "omni-connect.market.foreign-ownership-cache.mode=in-memory"
})
class JdbcNewsProcessingRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-07-22T10:00:00Z");

    @Autowired
    private NewsProcessingRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void deleteJobs() {
        jdbcTemplate.update("DELETE FROM news_processing_job");
    }

    @Test
    void enqueueIsIdempotentAndClaimsDifferentStocksFairly() {
        assertThat(repository.enqueue(pendingJob("job-a1", "005930", NOW.minusSeconds(10)), NOW)).isTrue();
        assertThat(repository.enqueue(pendingJob("job-a1", "005930", NOW.minusSeconds(10)), NOW)).isFalse();
        repository.enqueue(pendingJob("job-a2", "005930", NOW.minusSeconds(20)), NOW.plusSeconds(1));
        repository.enqueue(pendingJob("job-b1", "030200", NOW.minusSeconds(30)), NOW.plusSeconds(2));

        NewsProcessingJob first = repository.claimNext(NOW.plusSeconds(3), Duration.ofMinutes(45)).orElseThrow();
        NewsProcessingJob second = repository.claimNext(NOW.plusSeconds(3), Duration.ofMinutes(45)).orElseThrow();

        assertThat(first.stockCode()).isEqualTo("005930");
        assertThat(second.stockCode()).isEqualTo("030200");
        assertThat(first.leaseToken()).isNotBlank();
    }

    private NewsProcessingJob pendingJob(String jobId, String stockCode, Instant publishedAt) {
        return new NewsProcessingJob(
                jobId, "omni-connect-default-universe", stockCode,
                stockCode + " 주가 뉴스", "주가와 실적 기사",
                "https://news.example.com/" + jobId, publishedAt,
                "완전한 기사 본문입니다. ".repeat(20), "", "", "hash", "NAVER_SEARCH",
                "PENDING", 0, null);
    }
}

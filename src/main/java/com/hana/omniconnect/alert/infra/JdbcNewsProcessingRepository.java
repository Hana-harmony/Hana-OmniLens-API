package com.hana.omniconnect.alert.infra;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.hana.omniconnect.alert.application.NewsProcessingJob;
import com.hana.omniconnect.alert.application.NewsProcessingRepository;

@Repository
public class JdbcNewsProcessingRepository implements NewsProcessingRepository {

    private static final String CLAIMABLE_STATUS_SQL = """
            ((status IN ('PENDING', 'RETRY') AND next_attempt_at <= ?)
             OR (status = 'PROCESSING' AND lease_until < ?))
            """;

    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<NewsProcessingJob> rowMapper = new NewsProcessingJobRowMapper();

    public JdbcNewsProcessingRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public boolean enqueue(NewsProcessingJob job, Instant now) {
        try {
            jdbcTemplate.update(
                    """
                    INSERT INTO news_processing_job (
                        job_id, partner_id, stock_code, title, snippet, original_url,
                        published_at, source_content, image_urls, canonical_url,
                        content_hash, source_license_policy, status, attempt_count,
                        next_attempt_at, created_at, updated_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', 0, ?, ?, ?)
                    """,
                    job.jobId(), job.partnerId(), job.stockCode(), job.title(), job.snippet(),
                    job.originalUrl(), Timestamp.from(job.publishedAt()), job.sourceContent(),
                    job.imageUrls(), job.canonicalUrl(), job.contentHash(), job.sourceLicensePolicy(),
                    Timestamp.from(now), Timestamp.from(now), Timestamp.from(now));
            return true;
        } catch (DuplicateKeyException exception) {
            // 여러 수집 인스턴스가 같은 기사를 발견해도 분석 작업은 하나만 유지한다.
            return false;
        }
    }

    @Override
    @Transactional
    public Optional<NewsProcessingJob> claimNext(Instant now, Duration leaseDuration) {
        List<NewsProcessingJob> candidates = jdbcTemplate.query(
                """
                WITH stock_jobs AS (
                    SELECT job.*,
                           MAX(CASE WHEN attempt_count > 0 THEN updated_at END)
                               OVER (PARTITION BY partner_id, stock_code) AS stock_last_attempt_at
                    FROM news_processing_job job
                ), ranked AS (
                    SELECT stock_jobs.*,
                           ROW_NUMBER() OVER (
                               PARTITION BY partner_id, stock_code
                               ORDER BY attempt_count ASC, published_at DESC, created_at ASC
                           ) AS stock_rank
                    FROM stock_jobs
                    WHERE
                """ + CLAIMABLE_STATUS_SQL + """
                )
                SELECT job_id, partner_id, stock_code, title, snippet, original_url,
                       published_at, source_content, image_urls, canonical_url, content_hash,
                       source_license_policy, status, attempt_count, lease_token
                FROM ranked
                WHERE stock_rank = 1
                ORDER BY attempt_count ASC,
                         stock_last_attempt_at ASC NULLS FIRST,
                         published_at DESC,
                         created_at ASC
                LIMIT 64
                """,
                rowMapper,
                Timestamp.from(now),
                Timestamp.from(now));
        for (NewsProcessingJob candidate : candidates) {
            String leaseToken = UUID.randomUUID().toString();
            int claimed = jdbcTemplate.update(
                    """
                    UPDATE news_processing_job
                    SET status = 'PROCESSING',
                        attempt_count = attempt_count + 1,
                        lease_token = ?,
                        lease_until = ?,
                        updated_at = ?
                    WHERE job_id = ?
                      AND
                    """ + CLAIMABLE_STATUS_SQL,
                    leaseToken,
                    Timestamp.from(now.plus(leaseDuration)),
                    Timestamp.from(now),
                    candidate.jobId(),
                    Timestamp.from(now),
                    Timestamp.from(now));
            if (claimed == 1) {
                return Optional.of(new NewsProcessingJob(
                        candidate.jobId(), candidate.partnerId(), candidate.stockCode(),
                        candidate.title(), candidate.snippet(), candidate.originalUrl(),
                        candidate.publishedAt(), candidate.sourceContent(), candidate.imageUrls(),
                        candidate.canonicalUrl(), candidate.contentHash(), candidate.sourceLicensePolicy(),
                        "PROCESSING", candidate.attemptCount() + 1, leaseToken));
            }
        }
        return Optional.empty();
    }

    @Override
    public void markReady(NewsProcessingJob job, String alertId, Instant now) {
        finish(job, "READY", alertId, null, now);
    }

    @Override
    public void markRejected(NewsProcessingJob job, String reason, Instant now) {
        finish(job, "REJECTED", null, reason, now);
    }

    @Override
    public void scheduleRetry(NewsProcessingJob job, String reason, Instant nextAttemptAt, Instant now) {
        jdbcTemplate.update(
                """
                UPDATE news_processing_job
                SET status = 'RETRY', next_attempt_at = ?, lease_token = NULL,
                    lease_until = NULL, last_error = ?, updated_at = ?
                WHERE job_id = ? AND status = 'PROCESSING' AND lease_token = ?
                """,
                Timestamp.from(nextAttemptAt), truncate(reason), Timestamp.from(now),
                job.jobId(), job.leaseToken());
    }

    private void finish(NewsProcessingJob job, String status, String alertId, String reason, Instant now) {
        jdbcTemplate.update(
                """
                UPDATE news_processing_job
                SET status = ?, alert_id = ?, lease_token = NULL, lease_until = NULL,
                    last_error = ?, updated_at = ?
                WHERE job_id = ? AND status = 'PROCESSING' AND lease_token = ?
                """,
                status, alertId, truncate(reason), Timestamp.from(now), job.jobId(), job.leaseToken());
    }

    private static String truncate(String value) {
        return value == null || value.length() <= 1_000 ? value : value.substring(0, 1_000);
    }

    private static class NewsProcessingJobRowMapper implements RowMapper<NewsProcessingJob> {
        @Override
        public NewsProcessingJob mapRow(ResultSet resultSet, int rowNum) throws SQLException {
            return new NewsProcessingJob(
                    resultSet.getString("job_id"), resultSet.getString("partner_id"),
                    resultSet.getString("stock_code"), resultSet.getString("title"),
                    resultSet.getString("snippet"), resultSet.getString("original_url"),
                    resultSet.getTimestamp("published_at").toInstant(),
                    resultSet.getString("source_content"), resultSet.getString("image_urls"),
                    resultSet.getString("canonical_url"), resultSet.getString("content_hash"),
                    resultSet.getString("source_license_policy"), resultSet.getString("status"),
                    resultSet.getInt("attempt_count"), resultSet.getString("lease_token"));
        }
    }
}

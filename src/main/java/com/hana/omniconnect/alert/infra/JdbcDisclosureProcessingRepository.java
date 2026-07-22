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

import com.hana.omniconnect.alert.application.DisclosureProcessingJob;
import com.hana.omniconnect.alert.application.DisclosureProcessingRepository;

@Repository
public class JdbcDisclosureProcessingRepository implements DisclosureProcessingRepository {

    private static final String CLAIMABLE_STATUS_SQL = """
            ((status IN ('PENDING', 'RETRY') AND next_attempt_at <= ?)
             OR (status = 'PROCESSING' AND lease_until < ?))
            """;

    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<DisclosureProcessingJob> rowMapper = new DisclosureProcessingJobRowMapper();

    public JdbcDisclosureProcessingRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public boolean enqueue(DisclosureProcessingJob job, Instant now) {
        int updated = jdbcTemplate.update(
                """
                UPDATE disclosure_processing_job
                SET corporation_name = ?,
                    report_name = ?,
                    receipt_number = ?,
                    published_at = ?,
                    updated_at = ?
                WHERE partner_id = ?
                  AND stock_code = ?
                  AND original_url = ?
                """,
                job.corporationName(),
                job.reportName(),
                job.receiptNumber(),
                Timestamp.from(job.publishedAt()),
                Timestamp.from(now),
                job.partnerId(),
                job.stockCode(),
                job.originalUrl());
        if (updated > 0) {
            return false;
        }
        try {
            jdbcTemplate.update(
                    """
                    INSERT INTO disclosure_processing_job (
                        job_id, partner_id, stock_code, receipt_number, corporation_name,
                        report_name, original_url, published_at, status, attempt_count,
                        next_attempt_at, created_at, updated_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', 0, ?, ?, ?)
                    """,
                    job.jobId(),
                    job.partnerId(),
                    job.stockCode(),
                    job.receiptNumber(),
                    job.corporationName(),
                    job.reportName(),
                    job.originalUrl(),
                    Timestamp.from(job.publishedAt()),
                    Timestamp.from(now),
                    Timestamp.from(now),
                    Timestamp.from(now));
            return true;
        } catch (DuplicateKeyException exception) {
            // 여러 수집 인스턴스가 같은 DART 접수번호를 동시에 발견해도 작업은 하나만 유지한다.
            return false;
        }
    }

    @Override
    @Transactional
    public Optional<DisclosureProcessingJob> claimNext(Instant now, Duration leaseDuration) {
        List<DisclosureProcessingJob> candidates = jdbcTemplate.query(
                """
                SELECT job_id, partner_id, stock_code, receipt_number, corporation_name,
                       report_name, original_url, published_at, source_content, content_hash,
                       source_license_policy, status, attempt_count, lease_token
                FROM disclosure_processing_job
                WHERE
                """ + CLAIMABLE_STATUS_SQL + """
                ORDER BY attempt_count ASC, published_at DESC, created_at ASC
                LIMIT 1
                FOR UPDATE SKIP LOCKED
                """,
                rowMapper,
                Timestamp.from(now),
                Timestamp.from(now));
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        DisclosureProcessingJob candidate = candidates.get(0);
        String leaseToken = UUID.randomUUID().toString();
        jdbcTemplate.update(
                """
                UPDATE disclosure_processing_job
                SET status = 'PROCESSING',
                    attempt_count = attempt_count + 1,
                    lease_token = ?,
                    lease_until = ?,
                    updated_at = ?
                WHERE job_id = ?
                """,
                leaseToken,
                Timestamp.from(now.plus(leaseDuration)),
                Timestamp.from(now),
                candidate.jobId());
        return Optional.of(new DisclosureProcessingJob(
                candidate.jobId(),
                candidate.partnerId(),
                candidate.stockCode(),
                candidate.receiptNumber(),
                candidate.corporationName(),
                candidate.reportName(),
                candidate.originalUrl(),
                candidate.publishedAt(),
                candidate.sourceContent(),
                candidate.contentHash(),
                candidate.sourceLicensePolicy(),
                "PROCESSING",
                candidate.attemptCount() + 1,
                leaseToken));
    }

    @Override
    public void saveSourceDocument(
            DisclosureProcessingJob job,
            String sourceContent,
            String contentHash,
            String sourceLicensePolicy,
            Instant now) {
        jdbcTemplate.update(
                """
                UPDATE disclosure_processing_job
                SET source_content = ?,
                    content_hash = ?,
                    source_license_policy = ?,
                    updated_at = ?
                WHERE job_id = ?
                  AND status = 'PROCESSING'
                  AND lease_token = ?
                """,
                sourceContent,
                contentHash,
                sourceLicensePolicy,
                Timestamp.from(now),
                job.jobId(),
                job.leaseToken());
    }

    @Override
    public void markReady(DisclosureProcessingJob job, String alertId, Instant now) {
        finish(job, "READY", alertId, null, now);
    }

    @Override
    public void markRejected(DisclosureProcessingJob job, String reason, Instant now) {
        finish(job, "REJECTED", null, reason, now);
    }

    @Override
    public void scheduleRetry(DisclosureProcessingJob job, String reason, Instant nextAttemptAt, Instant now) {
        jdbcTemplate.update(
                """
                UPDATE disclosure_processing_job
                SET status = 'RETRY',
                    next_attempt_at = ?,
                    lease_token = NULL,
                    lease_until = NULL,
                    last_error = ?,
                    updated_at = ?
                WHERE job_id = ?
                  AND status = 'PROCESSING'
                  AND lease_token = ?
                """,
                Timestamp.from(nextAttemptAt),
                truncate(reason),
                Timestamp.from(now),
                job.jobId(),
                job.leaseToken());
    }

    private void finish(
            DisclosureProcessingJob job,
            String status,
            String alertId,
            String reason,
            Instant now) {
        jdbcTemplate.update(
                """
                UPDATE disclosure_processing_job
                SET status = ?,
                    alert_id = ?,
                    lease_token = NULL,
                    lease_until = NULL,
                    last_error = ?,
                    updated_at = ?
                WHERE job_id = ?
                  AND status = 'PROCESSING'
                  AND lease_token = ?
                """,
                status,
                alertId,
                truncate(reason),
                Timestamp.from(now),
                job.jobId(),
                job.leaseToken());
    }

    private static String truncate(String value) {
        if (value == null || value.length() <= 1_000) {
            return value;
        }
        return value.substring(0, 1_000);
    }

    private static class DisclosureProcessingJobRowMapper implements RowMapper<DisclosureProcessingJob> {
        @Override
        public DisclosureProcessingJob mapRow(ResultSet resultSet, int rowNum) throws SQLException {
            return new DisclosureProcessingJob(
                    resultSet.getString("job_id"),
                    resultSet.getString("partner_id"),
                    resultSet.getString("stock_code"),
                    resultSet.getString("receipt_number"),
                    resultSet.getString("corporation_name"),
                    resultSet.getString("report_name"),
                    resultSet.getString("original_url"),
                    resultSet.getTimestamp("published_at").toInstant(),
                    resultSet.getString("source_content"),
                    resultSet.getString("content_hash"),
                    resultSet.getString("source_license_policy"),
                    resultSet.getString("status"),
                    resultSet.getInt("attempt_count"),
                    resultSet.getString("lease_token"));
        }
    }
}

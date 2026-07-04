package com.hana.omnilens.alert.infra;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.hana.omnilens.alert.application.EnglishNewsQualityGate;
import com.hana.omnilens.alert.application.AlertEventRepository;
import com.hana.omnilens.alert.domain.AlertEvent;

@Repository
public class JdbcAlertEventRepository implements AlertEventRepository {

    private static final String SUMMARY_QUALITY_CANDIDATE_FILTER = """
            lower(event_json) LIKE '%...%'
            OR lower(event_json) LIKE '%…%'
            OR lower(event_json) LIKE '%classified%'
            OR lower(event_json) LIKE '%importance%'
            OR lower(event_json) LIKE '%sentiment%'
            OR event_json LIKE '%중요도%'
            OR event_json LIKE '%감성%'
            OR event_json LIKE '%투자 권유%'
            OR event_json LIKE '%최종 판단%'
            OR event_json LIKE '%투자자 본인%'
            OR event_json ~ '"summaryLines"\\s*:\\s*null'
            OR event_json ~ '"what"\\s*:\\s*""'
            OR event_json ~ '"why"\\s*:\\s*""'
            OR event_json ~ '"impact"\\s*:\\s*""'
            OR event_json ~ '[가-힣]'
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final RowMapper<AlertEvent> rowMapper = new AlertEventRowMapper();

    public JdbcAlertEventRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public AlertEvent save(AlertEvent event) {
        int updated = jdbcTemplate.update(
                """
                UPDATE alert_event
                SET partner_id = ?,
                    stock_code = ?,
                    source_type = ?,
                    original_url = ?,
                    duplicate_key = ?,
                    cluster_key = ?,
                    content_availability = ?,
                    published_at = ?,
                    created_at = ?,
                    event_json = ?
                WHERE alert_id = ?
                """,
                event.partnerId(),
                event.stockCode(),
                event.sourceType(),
                event.originalUrl(),
                event.duplicateKey(),
                event.clusterKey(),
                event.contentAvailability(),
                Timestamp.from(event.publishedAt()),
                Timestamp.from(event.createdAt()),
                toJson(event),
                event.alertId());
        if (updated == 0) {
            jdbcTemplate.update(
                    """
                    INSERT INTO alert_event (
                        alert_id, partner_id, stock_code, source_type, original_url,
                        duplicate_key, cluster_key, content_availability, published_at, created_at, event_json
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    event.alertId(),
                    event.partnerId(),
                    event.stockCode(),
                    event.sourceType(),
                    event.originalUrl(),
                    event.duplicateKey(),
                    event.clusterKey(),
                    event.contentAvailability(),
                    Timestamp.from(event.publishedAt()),
                    Timestamp.from(event.createdAt()),
                    toJson(event));
        }
        return event;
    }

    @Override
    public Optional<AlertEvent> findByAlertId(String alertId) {
        return jdbcTemplate.query(
                        """
                        SELECT event_json
                        FROM alert_event
                        WHERE alert_id = ?
                        """,
                        rowMapper,
                        alertId)
                .stream()
                .findFirst();
    }

    @Override
    public List<AlertEvent> findByStockCode(String stockCode, int limit) {
        return jdbcTemplate.query(
                """
                SELECT event_json
                FROM alert_event
                WHERE stock_code = ?
                ORDER BY published_at DESC, created_at DESC
                LIMIT ?
                """,
                rowMapper,
                stockCode,
                limit);
    }

    @Override
    public List<AlertEvent> findSummaryQualityIssues(int limit) {
        int candidateLimit = Math.min(Math.max(limit * 50, limit), 10_000);
        return jdbcTemplate.queryForList(
                """
                SELECT event_json
                FROM alert_event
                WHERE (
                """ + SUMMARY_QUALITY_CANDIDATE_FILTER + """
                )
                ORDER BY published_at DESC, created_at DESC
                LIMIT ?
                """,
                String.class,
                candidateLimit)
                .stream()
                .map(this::readEvent)
                .flatMap(Optional::stream)
                .filter(this::hasSummaryQualityIssue)
                .limit(limit)
                .toList();
    }

    private String toJson(AlertEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize alert event", exception);
        }
    }

    private Optional<AlertEvent> readEvent(String eventJson) {
        try {
            return Optional.of(objectMapper.readValue(eventJson, AlertEvent.class));
        } catch (JsonProcessingException exception) {
            return Optional.empty();
        }
    }

    private boolean hasSummaryQualityIssue(AlertEvent event) {
        if (event.summaryLines() == null
                || isBlank(event.summaryLines().what())
                || isBlank(event.summaryLines().why())
                || isBlank(event.summaryLines().impact())
                || !EnglishNewsQualityGate.hasUsableEnglishSummaryLines(event.summaryLines())) {
            return true;
        }
        String summaryLines = event.summaryLines() == null
                ? ""
                : String.join(" ",
                        nullToEmpty(event.summaryLines().what()),
                        nullToEmpty(event.summaryLines().why()),
                        nullToEmpty(event.summaryLines().impact()));
        String payload = String.join(" ",
                nullToEmpty(event.translatedSummary()),
                summaryLines);
        String lower = payload.toLowerCase(Locale.ROOT);
        return EnglishNewsQualityGate.containsHangul(event.translatedSummary())
                || EnglishNewsQualityGate.containsHangul(event.translatedContent())
                || lower.contains("...")
                || lower.contains("…")
                || lower.contains("classified")
                || lower.contains("importance")
                || lower.contains("sentiment")
                || payload.contains("중요도")
                || payload.contains("감성")
                || payload.contains("투자 권유")
                || payload.contains("최종 판단")
                || payload.contains("투자자 본인");
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private class AlertEventRowMapper implements RowMapper<AlertEvent> {

        @Override
        public AlertEvent mapRow(ResultSet resultSet, int rowNum) throws SQLException {
            try {
                return objectMapper.readValue(resultSet.getString("event_json"), AlertEvent.class);
            } catch (JsonProcessingException exception) {
                throw new SQLException("Failed to deserialize alert event", exception);
            }
        }
    }
}

package com.hana.omnilens.alert.infra;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.hana.omnilens.alert.application.AlertEventRepository;
import com.hana.omnilens.alert.domain.AlertEvent;

@Repository
public class JdbcAlertEventRepository implements AlertEventRepository {

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

    private String toJson(AlertEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize alert event", exception);
        }
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

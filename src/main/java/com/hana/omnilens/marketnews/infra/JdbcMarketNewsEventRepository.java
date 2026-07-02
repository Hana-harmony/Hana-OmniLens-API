package com.hana.omnilens.marketnews.infra;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hana.omnilens.marketnews.application.MarketNewsEventRepository;
import com.hana.omnilens.marketnews.domain.MarketNewsEvent;

@Repository
public class JdbcMarketNewsEventRepository implements MarketNewsEventRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final RowMapper<MarketNewsEvent> rowMapper = new MarketNewsEventRowMapper();

    public JdbcMarketNewsEventRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public MarketNewsEvent save(MarketNewsEvent event) {
        try {
            jdbcTemplate.update(
                    """
                    INSERT INTO market_news_event (
                        news_id, query, original_url, duplicate_key, published_at, created_at, event_json
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """,
                    event.newsId(),
                    event.query(),
                    event.originalUrl(),
                    event.duplicateKey(),
                    Timestamp.from(event.publishedAt()),
                    Timestamp.from(event.createdAt()),
                    toJson(event));
            return event;
        } catch (DuplicateKeyException exception) {
            return findByDuplicateKey(event.duplicateKey()).orElse(event);
        }
    }

    @Override
    public Optional<MarketNewsEvent> findByNewsId(String newsId) {
        return jdbcTemplate.query(
                        """
                        SELECT event_json
                        FROM market_news_event
                        WHERE news_id = ?
                        """,
                        rowMapper,
                        newsId)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<MarketNewsEvent> findByDuplicateKey(String duplicateKey) {
        return jdbcTemplate.query(
                        """
                        SELECT event_json
                        FROM market_news_event
                        WHERE duplicate_key = ?
                        """,
                        rowMapper,
                        duplicateKey)
                .stream()
                .findFirst();
    }

    @Override
    public List<MarketNewsEvent> findLatest(int limit) {
        return jdbcTemplate.query(
                """
                SELECT event_json
                FROM market_news_event
                ORDER BY published_at DESC, created_at DESC
                LIMIT ?
                """,
                rowMapper,
                limit);
    }

    @Override
    public void recordView(String newsId, Instant viewedAt) {
        jdbcTemplate.update(
                """
                INSERT INTO market_news_view_event (news_id, viewed_at)
                VALUES (?, ?)
                """,
                newsId,
                Timestamp.from(viewedAt));
    }

    @Override
    public List<MarketNewsEvent> findTrending(Instant since, int limit) {
        return jdbcTemplate.query(
                """
                SELECT event.event_json
                FROM market_news_event event
                JOIN market_news_view_event view_event
                  ON view_event.news_id = event.news_id
                WHERE view_event.viewed_at >= ?
                GROUP BY event.news_id, event.event_json
                ORDER BY COUNT(view_event.view_id) DESC,
                         MAX(view_event.viewed_at) DESC,
                         MAX(event.published_at) DESC
                LIMIT ?
                """,
                rowMapper,
                Timestamp.from(since),
                limit);
    }

    private String toJson(MarketNewsEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize market news event", exception);
        }
    }

    private class MarketNewsEventRowMapper implements RowMapper<MarketNewsEvent> {

        @Override
        public MarketNewsEvent mapRow(ResultSet resultSet, int rowNum) throws SQLException {
            try {
                return objectMapper.readValue(resultSet.getString("event_json"), MarketNewsEvent.class);
            } catch (JsonProcessingException exception) {
                throw new SQLException("Failed to deserialize market news event", exception);
            }
        }
    }
}

package com.hana.omnilens.term.infra;

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
import com.hana.omnilens.term.application.KoreanFinancialTermClickLog;
import com.hana.omnilens.term.application.KoreanFinancialTermExplanationCacheEntry;
import com.hana.omnilens.term.application.KoreanFinancialTermExplanationRepository;
import com.hana.omnilens.term.domain.KoreanFinancialTermClickStat;
import com.hana.omnilens.term.domain.KoreanFinancialTermClickPoint;
import com.hana.omnilens.term.domain.KoreanFinancialTermExplanation;

@Repository
public class JdbcKoreanFinancialTermExplanationRepository implements KoreanFinancialTermExplanationRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final RowMapper<KoreanFinancialTermExplanationCacheEntry> cacheRowMapper = new CacheRowMapper();
    private final RowMapper<KoreanFinancialTermClickStat> statRowMapper = new StatRowMapper();

    public JdbcKoreanFinancialTermExplanationRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<KoreanFinancialTermExplanationCacheEntry> findValidCache(String cacheKey, Instant now) {
        return jdbcTemplate.query(
                        """
                        SELECT cache_key, term, normalized_term, locale, article_id, stock_code, source,
                               display_mode, cacheable, expires_at, response_json
                        FROM korean_financial_term_explanation_cache
                        WHERE cache_key = ? AND expires_at > ?
                        """,
                        cacheRowMapper,
                        cacheKey,
                        Timestamp.from(now))
                .stream()
                .findFirst();
    }

    @Override
    @Transactional
    public void upsertCache(KoreanFinancialTermExplanationCacheEntry entry, Instant now) {
        int updated = jdbcTemplate.update(
                """
                UPDATE korean_financial_term_explanation_cache
                SET term = ?, normalized_term = ?, locale = ?, article_id = ?, stock_code = ?, source = ?,
                    display_mode = ?, cacheable = ?, expires_at = ?, updated_at = ?, response_json = ?
                WHERE cache_key = ?
                """,
                entry.term(),
                entry.normalizedTerm(),
                entry.locale(),
                entry.articleId(),
                entry.stockCode(),
                entry.source(),
                entry.displayMode(),
                entry.cacheable(),
                Timestamp.from(entry.expiresAt()),
                Timestamp.from(now),
                toJson(entry.response()),
                entry.cacheKey());
        if (updated > 0) {
            return;
        }
        try {
            jdbcTemplate.update(
                    """
                    INSERT INTO korean_financial_term_explanation_cache (
                        cache_key, term, normalized_term, locale, article_id, stock_code, source,
                        display_mode, cacheable, expires_at, created_at, updated_at, response_json
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    entry.cacheKey(),
                    entry.term(),
                    entry.normalizedTerm(),
                    entry.locale(),
                    entry.articleId(),
                    entry.stockCode(),
                    entry.source(),
                    entry.displayMode(),
                    entry.cacheable(),
                    Timestamp.from(entry.expiresAt()),
                    Timestamp.from(now),
                    Timestamp.from(now),
                    toJson(entry.response()));
        } catch (DuplicateKeyException exception) {
            upsertCache(entry, now);
        }
    }

    @Override
    @Transactional
    public long recordClick(KoreanFinancialTermClickLog clickLog) {
        jdbcTemplate.update(
                """
                INSERT INTO korean_financial_term_click_log (
                    click_id, occurred_at, term, normalized_term, locale, source_type, article_id,
                    article_url, stock_code, stock_name, user_hash, session_hash, cache_hit,
                    explanation_source, display_mode
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                clickLog.clickId(),
                Timestamp.from(clickLog.occurredAt()),
                clickLog.term(),
                clickLog.normalizedTerm(),
                clickLog.locale(),
                clickLog.sourceType(),
                clickLog.articleId(),
                clickLog.articleUrl(),
                clickLog.stockCode(),
                clickLog.stockName(),
                clickLog.userHash(),
                clickLog.sessionHash(),
                clickLog.cacheHit(),
                clickLog.explanationSource(),
                clickLog.displayMode());
        upsertStats(clickLog);
        return currentClickCount(clickLog.normalizedTerm(), clickLog.locale());
    }

    @Override
    public List<KoreanFinancialTermClickStat> findTopStats(int limit) {
        return jdbcTemplate.query(
                """
                SELECT normalized_term, locale, click_count, cache_hit_count, review_required_count,
                       first_clicked_at, last_clicked_at
                FROM korean_financial_term_click_stats
                ORDER BY click_count DESC, last_clicked_at DESC
                LIMIT ?
                """,
                statRowMapper,
                limit);
    }

    @Override
    public List<KoreanFinancialTermClickPoint> findClickSeries(String period) {
        String normalized = period == null ? "DAY" : period.toUpperCase(java.util.Locale.ROOT);
        String bucket = switch (normalized) {
            case "MONTH" -> "month";
            case "YEAR" -> "year";
            case "ALL" -> null;
            default -> "day";
        };
        if (bucket == null) {
            return jdbcTemplate.query(
                    "SELECT MIN(occurred_at) period_start, COUNT(*) click_count FROM korean_financial_term_click_log HAVING COUNT(*) > 0",
                    (resultSet, rowNumber) -> new KoreanFinancialTermClickPoint(
                            resultSet.getTimestamp("period_start").toInstant(),
                            resultSet.getLong("click_count")));
        }
        return jdbcTemplate.query(
                "SELECT date_trunc('" + bucket + "', occurred_at) period_start, COUNT(*) click_count "
                        + "FROM korean_financial_term_click_log GROUP BY period_start ORDER BY period_start",
                (resultSet, rowNumber) -> new KoreanFinancialTermClickPoint(
                        resultSet.getTimestamp("period_start").toInstant(),
                        resultSet.getLong("click_count")));
    }

    private void upsertStats(KoreanFinancialTermClickLog clickLog) {
        int updated = jdbcTemplate.update(
                """
                UPDATE korean_financial_term_click_stats
                SET click_count = click_count + 1,
                    cache_hit_count = cache_hit_count + ?,
                    review_required_count = review_required_count + ?,
                    last_clicked_at = ?
                WHERE normalized_term = ? AND locale = ?
                """,
                clickLog.cacheHit() ? 1 : 0,
                "REVIEW_REQUIRED".equals(clickLog.displayMode()) ? 1 : 0,
                Timestamp.from(clickLog.occurredAt()),
                clickLog.normalizedTerm(),
                clickLog.locale());
        if (updated > 0) {
            return;
        }
        try {
            jdbcTemplate.update(
                    """
                    INSERT INTO korean_financial_term_click_stats (
                        normalized_term, locale, click_count, cache_hit_count, review_required_count,
                        first_clicked_at, last_clicked_at
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """,
                    clickLog.normalizedTerm(),
                    clickLog.locale(),
                    1,
                    clickLog.cacheHit() ? 1 : 0,
                    "REVIEW_REQUIRED".equals(clickLog.displayMode()) ? 1 : 0,
                    Timestamp.from(clickLog.occurredAt()),
                    Timestamp.from(clickLog.occurredAt()));
        } catch (DuplicateKeyException exception) {
            upsertStats(clickLog);
        }
    }

    private long currentClickCount(String normalizedTerm, String locale) {
        Long count = jdbcTemplate.queryForObject(
                """
                SELECT click_count
                FROM korean_financial_term_click_stats
                WHERE normalized_term = ? AND locale = ?
                """,
                Long.class,
                normalizedTerm,
                locale);
        return count == null ? 0 : count;
    }

    private String toJson(KoreanFinancialTermExplanation response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize Korean financial term explanation", exception);
        }
    }

    private KoreanFinancialTermExplanation fromJson(String responseJson) throws SQLException {
        try {
            return objectMapper.readValue(responseJson, KoreanFinancialTermExplanation.class);
        } catch (JsonProcessingException exception) {
            throw new SQLException("Failed to deserialize Korean financial term explanation", exception);
        }
    }

    private class CacheRowMapper implements RowMapper<KoreanFinancialTermExplanationCacheEntry> {

        @Override
        public KoreanFinancialTermExplanationCacheEntry mapRow(ResultSet resultSet, int rowNum) throws SQLException {
            return new KoreanFinancialTermExplanationCacheEntry(
                    resultSet.getString("cache_key"),
                    resultSet.getString("term"),
                    resultSet.getString("normalized_term"),
                    resultSet.getString("locale"),
                    resultSet.getString("article_id"),
                    resultSet.getString("stock_code"),
                    resultSet.getString("source"),
                    resultSet.getString("display_mode"),
                    resultSet.getBoolean("cacheable"),
                    resultSet.getTimestamp("expires_at").toInstant(),
                    fromJson(resultSet.getString("response_json")));
        }
    }

    private static class StatRowMapper implements RowMapper<KoreanFinancialTermClickStat> {

        @Override
        public KoreanFinancialTermClickStat mapRow(ResultSet resultSet, int rowNum) throws SQLException {
            return new KoreanFinancialTermClickStat(
                    resultSet.getString("normalized_term"),
                    resultSet.getString("locale"),
                    resultSet.getLong("click_count"),
                    resultSet.getLong("cache_hit_count"),
                    resultSet.getLong("review_required_count"),
                    resultSet.getTimestamp("first_clicked_at").toInstant(),
                    resultSet.getTimestamp("last_clicked_at").toInstant());
        }
    }
}

package com.hana.omniconnect.alert.infra;

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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.hana.omniconnect.alert.application.EnglishNewsQualityGate;
import com.hana.omniconnect.alert.application.AlertEventRepository;
import com.hana.omniconnect.alert.domain.AlertEvent;
import com.hana.omniconnect.common.api.KeysetCursor;

@Repository
public class JdbcAlertEventRepository implements AlertEventRepository {

    private static final String SUMMARY_QUALITY_CANDIDATE_FILTER = """
            lower(event_json) ~ '"(translatedsummary|translatedcontent|what|why|impact)"\\s*:\\s*"[^"]*(\\.\\.\\.|…|classified|i''m sorry|i can’t assist|i can''t assist|please provide|as an ai|publisher of this newspaper|columnist|this item covers|latest market or company context confirmed in the source article|investors should review possible effects on prices, earnings, liquidity, and watched holdings|drew attention in the article|article-backed market context|next disclosure and market reaction|confirmed in the latest news|confirmed in the latest disclosure|the story links the shift to supply|the story links the shift to the article|investors should follow the next disclosure and watch the market reaction)'
            OR lower(event_json) ~ '"translatedcontent"\\s*:\\s*"what:[^"]*why:[^"]*impact:'
            OR lower(event_json) ~ 'the original korean text is retained because machine translation was unavailable'
            OR lower(event_json) ~ '"(translatedtitle|translatedsummary|translatedcontent|what|why|impact)"\\s*:\\s*"[^"]*(korean company update|korean market update)'
            OR lower(event_json) ~ '"(translatedtitle|translatedsummary|translatedcontent|what|why|impact)"\\s*:\\s*"[^"]*(kang nam-go|pab-wo|dda-jeon|levership|hannak|defi-shares|nanyang dynamics|snicklever|stock-celltrion|hanacorp|sina-combankipt|lg-hydration|iong-wok|nalmalai|without a street|according to a search|three-sentence|effect of the number|dynamic of the|nmsk|auction raise|auction distributor|exchange order|cosby market|capacitor semiconductor|chinese p&t7|lithium supply|dividend price of equity dividends|hanoteoreminder|hyang-yeol|yuseo|hidden world history|korean farmer''s 600-year|fresh water break|i''m going to|power-driven|two-carpet|new bond''s price flow|flowing semiconductor ship|on strike; the actuality|entering the ''sides''|triangle lower limited|us-exited ai-investor|samjeon nix''s trading method does not exist|samjeon nok|future-sustainable capital|adding silicon|european shopping trip|samnick|middle and small businesses fund acts|investors net at the european show|no ai or human|reveal ourselves|countermeasures inspection|approval of the megaproject|core themes of ai and human death|latest market and company interventions|market and business events confirmed|trading by samjeon nix|by samjeon nix as key|latest public news confirmed in the original|impact of this president|holding and surveillance|samjeon nix trading|latest market and corporate events confirmed|krw-3777b|sheriff''s rifle|iseutasi|investor''s net buying flow|entrepreneurhan|hallinkyos|sk hallinkyos|skhinky|sinerlwyk|hyanix|skhynx|klamath stock exchange|north american and south american trade disputes|substitute offering|high-slang|teatr esg|tutat esg|hyundai motor, kia, and mercedes-benz|car insurance and vehicle services|freaked out about the deposits|triple-a hynix|truck-train|kospi faced the kospi market move|investor impact is higher on the flow of earnings|investor impact is higher on ev and hev markets|foreign exchanges as the market becomes more active|national association of churches|18 temples|90 trillion yuan|receivable volume|reception function|periodic allowance|the headline also references)'
            OR lower(event_json) ~ '"(what|why|impact)"\\s*:\\s*"[^"]*[a-z0-9]"'
            OR event_json ~ '"summaryLines"\\s*:\\s*null'
            OR event_json ~ '"what"\\s*:\\s*""'
            OR event_json ~ '"why"\\s*:\\s*""'
            OR event_json ~ '"impact"\\s*:\\s*""'
            OR (event_json ~ '"originalContent"\\s*:\\s*"[^"]+' AND event_json ~ '"translatedContent"\\s*:\\s*(null|"")')
            OR event_json ~ '"translatedTitle"\\s*:\\s*"[^"]*[가-힣]'
            OR event_json ~ '"translatedSummary"\\s*:\\s*"[^"]*[가-힣]'
            OR event_json ~ '"translatedContent"\\s*:\\s*"[^"]*[가-힣]'
            OR event_json ~ '"summaryLines"\\s*:\\s*\\{[^}]*[가-힣]'
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
        Optional<AlertEvent> existing = findBySourceIdentity(
                event.partnerId(),
                event.stockCode(),
                event.sourceType(),
                event.originalUrl());
        if (existing.isPresent() && !existing.orElseThrow().alertId().equals(event.alertId())) {
            return existing.orElseThrow();
        }
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
            try {
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
            } catch (DuplicateKeyException exception) {
                // 다중 인스턴스가 동시에 수집해도 먼저 저장된 이벤트를 재사용한다.
                return findBySourceIdentity(
                                event.partnerId(),
                                event.stockCode(),
                                event.sourceType(),
                                event.originalUrl())
                        .orElseThrow(() -> exception);
            }
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
    public Optional<AlertEvent> findBySourceIdentity(
            String partnerId,
            String stockCode,
            String sourceType,
            String originalUrl) {
        return jdbcTemplate.query(
                        """
                        SELECT event_json
                        FROM alert_event
                        WHERE partner_id = ?
                          AND stock_code = ?
                          AND source_type = ?
                          AND original_url = ?
                        ORDER BY created_at DESC
                        LIMIT 1
                        """,
                        rowMapper,
                        partnerId,
                        stockCode,
                        sourceType,
                        originalUrl)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<AlertEvent> findByDuplicateIdentity(
            String partnerId,
            String stockCode,
            String sourceType,
            String duplicateKey) {
        return jdbcTemplate.query(
                        """
                        SELECT event_json
                        FROM alert_event
                        WHERE partner_id = ?
                          AND stock_code = ?
                          AND source_type = ?
                          AND duplicate_key = ?
                        ORDER BY created_at DESC
                        LIMIT 1
                        """,
                        rowMapper,
                        partnerId,
                        stockCode,
                        sourceType,
                        duplicateKey)
                .stream()
                .findFirst();
    }

    @Override
    public int countByPartnerStockAndSourceType(
            String partnerId,
            String stockCode,
            String sourceType) {
        return (int) jdbcTemplate.queryForList(
                """
                SELECT event_json
                FROM alert_event
                WHERE partner_id = ?
                  AND stock_code = ?
                  AND source_type = ?
                  AND content_availability = 'FULL_TEXT'
                """,
                String.class,
                partnerId,
                stockCode,
                sourceType)
                .stream()
                .map(this::readEvent)
                .flatMap(Optional::stream)
                .filter(this::isPublishReady)
                .count();
    }

    @Override
    public Optional<AlertEvent> findLatestByPartnerStockAndSourceType(
            String partnerId,
            String stockCode,
            String sourceType) {
        return jdbcTemplate.query(
                        """
                        SELECT event_json
                        FROM alert_event
                        WHERE partner_id = ?
                          AND stock_code = ?
                          AND source_type = ?
                          AND content_availability = 'FULL_TEXT'
                        ORDER BY published_at DESC, created_at DESC, alert_id DESC
                        """,
                        rowMapper,
                        partnerId,
                        stockCode,
                        sourceType)
                .stream()
                .filter(this::isPublishReady)
                .findFirst();
    }

    @Override
    public List<AlertEvent> findByStockCode(String stockCode, int limit) {
        return jdbcTemplate.query(
                """
                SELECT event_json
                FROM alert_event
                WHERE stock_code = ?
                ORDER BY published_at DESC, created_at DESC, alert_id DESC
                LIMIT ?
                """,
                rowMapper,
                stockCode,
                limit);
    }

    @Override
    public List<AlertEvent> findByStockCodeBefore(String stockCode, KeysetCursor cursor, int limit) {
        if (cursor == null) {
            return findByStockCode(stockCode, limit);
        }
        return jdbcTemplate.query(
                """
                SELECT event_json
                FROM alert_event
                WHERE stock_code = ?
                  AND (
                    published_at < ?
                    OR (published_at = ? AND created_at < ?)
                    OR (published_at = ? AND created_at = ? AND alert_id < ?)
                  )
                ORDER BY published_at DESC, created_at DESC, alert_id DESC
                LIMIT ?
                """,
                rowMapper,
                stockCode,
                Timestamp.from(cursor.publishedAt()),
                Timestamp.from(cursor.publishedAt()),
                Timestamp.from(cursor.createdAt()),
                Timestamp.from(cursor.publishedAt()),
                Timestamp.from(cursor.createdAt()),
                cursor.id(),
                limit);
    }

    @Override
    public List<AlertEvent> findByStockCodeAndSourceType(String stockCode, String sourceType, int limit) {
        return jdbcTemplate.query(
                """
                SELECT event_json
                FROM alert_event
                WHERE stock_code = ?
                  AND source_type = ?
                ORDER BY published_at DESC, created_at DESC
                LIMIT ?
                """,
                rowMapper,
                stockCode,
                sourceType,
                limit);
    }

    @Override
    public List<AlertEvent> findSummaryQualityIssues(int limit) {
        int candidateLimit = Math.min(Math.max(limit * 100, 1_000), 10_000);
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
        if (!isBlank(event.originalContent()) && isBlank(event.translatedContent())) {
            return true;
        }
        if (!isBlank(event.originalContent())
                && EnglishNewsQualityGate.looksLikeSummaryOnlyContent(
                        event.translatedContent(),
                        event.summaryLines(),
                        event.translatedSummary(),
                        event.originalContent())) {
            return true;
        }
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
                || EnglishNewsQualityGate.containsGenericFallback(event.translatedTitle())
                || EnglishNewsQualityGate.containsGenericFallback(payload)
                || EnglishNewsQualityGate.containsGenericFallback(event.translatedContent())
                || EnglishNewsQualityGate.containsLowQualityTranslation(event.translatedTitle())
                || EnglishNewsQualityGate.containsLowQualityTranslation(payload)
                || EnglishNewsQualityGate.containsLowQualityTranslation(event.translatedContent())
                || lower.contains("...")
                || lower.contains("…")
                || lower.contains("classified")
                || lower.contains("importance")
                || lower.contains("sentiment")
                || lower.contains("i'm sorry")
                || lower.contains("i can’t assist")
                || lower.contains("i can't assist")
                || lower.contains("please provide")
                || lower.contains("as an ai")
                || lower.contains("publisher of this newspaper")
                || lower.contains("columnist")
                || payload.contains("중요도")
                || payload.contains("감성")
                || payload.contains("투자 권유")
                || payload.contains("최종 판단")
                || payload.contains("투자자 본인");
    }

    private boolean isPublishReady(AlertEvent event) {
        return event != null
                && !isBlank(event.originalContent())
                && EnglishNewsQualityGate.hasUsableEnglishSummaryLines(event.summaryLines())
                && EnglishNewsQualityGate.hasUsableEnglishText(event.translatedContent())
                && !EnglishNewsQualityGate.looksLikeSummaryOnlyContent(
                        event.translatedContent(),
                        event.summaryLines(),
                        event.translatedSummary(),
                        event.originalContent());
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

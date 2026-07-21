package com.hana.omniconnect.market.infra;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.hana.omniconnect.market.application.MarketIndexSnapshotRepository;
import com.hana.omniconnect.market.domain.MarketIndexIntradayPrice;
import com.hana.omniconnect.market.domain.MarketIndexQuote;

@Repository
public class JdbcMarketIndexSnapshotRepository implements MarketIndexSnapshotRepository {

    private static final RowMapper<MarketIndexQuote> INDEX_ROW_MAPPER = new MarketIndexQuoteRowMapper();
    private static final RowMapper<MarketIndexIntradayPrice> INTRADAY_ROW_MAPPER =
            new MarketIndexIntradayPriceRowMapper();

    private final JdbcTemplate jdbcTemplate;

    public JdbcMarketIndexSnapshotRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public void recordLatest(MarketIndexQuote indexQuote) {
        int updated = jdbcTemplate.update(
                """
                UPDATE market_index_latest_snapshot
                SET index_name = ?,
                    market = ?,
                    current_value = ?,
                    change_sign = ?,
                    change_value = ?,
                    change_rate = ?,
                    accumulated_volume = ?,
                    accumulated_trading_value = ?,
                    open_value = ?,
                    high_value = ?,
                    low_value = ?,
                    market_data_time = ?,
                    trade_date = ?,
                    source = ?,
                    collected_at = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE index_code = ?
                """,
                indexQuote.indexName(),
                indexQuote.market(),
                indexQuote.currentValue(),
                indexQuote.changeSign(),
                indexQuote.changeValue(),
                indexQuote.changeRate(),
                indexQuote.accumulatedVolume(),
                indexQuote.accumulatedTradingValue(),
                indexQuote.openValue(),
                indexQuote.highValue(),
                indexQuote.lowValue(),
                Timestamp.from(indexQuote.marketDataTime()),
                indexQuote.marketDataTime().atZone(java.time.ZoneId.of("Asia/Seoul")).toLocalDate(),
                indexQuote.source(),
                Timestamp.from(indexQuote.marketDataTime()),
                indexQuote.indexCode());
        if (updated == 0) {
            insertLatest(indexQuote);
        }
    }

    private void insertLatest(MarketIndexQuote indexQuote) {
        jdbcTemplate.update(
                """
                INSERT INTO market_index_latest_snapshot (
                    index_code, index_name, market, current_value, change_sign, change_value,
                    change_rate, accumulated_volume, accumulated_trading_value, open_value,
                    high_value, low_value, market_data_time, trade_date, source, collected_at, updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """,
                indexQuote.indexCode(),
                indexQuote.indexName(),
                indexQuote.market(),
                indexQuote.currentValue(),
                indexQuote.changeSign(),
                indexQuote.changeValue(),
                indexQuote.changeRate(),
                indexQuote.accumulatedVolume(),
                indexQuote.accumulatedTradingValue(),
                indexQuote.openValue(),
                indexQuote.highValue(),
                indexQuote.lowValue(),
                Timestamp.from(indexQuote.marketDataTime()),
                indexQuote.marketDataTime().atZone(java.time.ZoneId.of("Asia/Seoul")).toLocalDate(),
                indexQuote.source(),
                Timestamp.from(indexQuote.marketDataTime()));
    }

    @Override
    @Transactional
    public void recordRealtimeMinute(MarketIndexIntradayPrice price) {
        int updated = jdbcTemplate.update(
                """
                UPDATE market_index_intraday_minute_price
                SET index_name = ?,
                    market = ?,
                    high_value = CASE WHEN high_value >= ? THEN high_value ELSE ? END,
                    low_value = CASE WHEN low_value <= ? THEN low_value ELSE ? END,
                    close_value = ?,
                    trading_volume = ?,
                    trading_value_krw = ?,
                    source = ?,
                    collected_at = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE index_code = ?
                  AND bucket_start = ?
                """,
                price.indexName(),
                price.market(),
                price.highValue(),
                price.highValue(),
                price.lowValue(),
                price.lowValue(),
                price.closeValue(),
                price.tradingVolume(),
                price.tradingValueKrw(),
                price.source(),
                Timestamp.from(price.collectedAt()),
                price.indexCode(),
                Timestamp.valueOf(price.bucketStart()));
        if (updated == 0) {
            insertIntraday(price);
        }
    }

    @Override
    @Transactional
    public int upsertIntradayPrices(List<MarketIndexIntradayPrice> prices) {
        if (prices == null || prices.isEmpty()) {
            return 0;
        }
        prices.forEach(this::recordRealtimeMinute);
        return prices.size();
    }

    private void insertIntraday(MarketIndexIntradayPrice price) {
        jdbcTemplate.update(
                """
                INSERT INTO market_index_intraday_minute_price (
                    index_code, index_name, market, bucket_start, trade_date,
                    open_value, high_value, low_value, close_value,
                    trading_volume, trading_value_krw, source, collected_at, updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """,
                price.indexCode(),
                price.indexName(),
                price.market(),
                Timestamp.valueOf(price.bucketStart()),
                price.bucketStart().toLocalDate(),
                price.openValue(),
                price.highValue(),
                price.lowValue(),
                price.closeValue(),
                price.tradingVolume(),
                price.tradingValueKrw(),
                price.source(),
                Timestamp.from(price.collectedAt()));
    }

    @Override
    public List<MarketIndexQuote> findLatestIndices() {
        return jdbcTemplate.query(
                """
                SELECT index_code, index_name, market, current_value, change_sign, change_value,
                       change_rate, accumulated_volume, accumulated_trading_value, open_value,
                       high_value, low_value, market_data_time, source
                FROM market_index_latest_snapshot
                ORDER BY index_code ASC
                """,
                INDEX_ROW_MAPPER);
    }

    @Override
    public List<MarketIndexIntradayPrice> findIntraday(String indexCode, LocalDate date, int limit) {
        return jdbcTemplate.query(
                """
                SELECT index_code, index_name, market, bucket_start,
                       open_value, high_value, low_value, close_value,
                       trading_volume, trading_value_krw, source, collected_at
                FROM market_index_intraday_minute_price
                WHERE index_code = ?
                  AND trade_date = ?
                ORDER BY bucket_start ASC
                LIMIT ?
                """,
                INTRADAY_ROW_MAPPER,
                indexCode,
                date,
                limit);
    }

    @Override
    public Optional<MarketIndexIntradayPrice> findLatestBefore(String indexCode, LocalDate date) {
        return jdbcTemplate.query(
                        """
                        SELECT index_code, index_name, market, bucket_start,
                               open_value, high_value, low_value, close_value,
                               trading_volume, trading_value_krw, source, collected_at
                        FROM market_index_intraday_minute_price
                        WHERE index_code = ?
                          AND trade_date < ?
                        ORDER BY bucket_start DESC
                        LIMIT 1
                        """,
                        INTRADAY_ROW_MAPPER,
                        indexCode,
                        date)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<LocalDate> latestTradeDate(String indexCode) {
        LocalDate date = jdbcTemplate.query(
                """
                SELECT MAX(trade_date)
                FROM market_index_intraday_minute_price
                WHERE index_code = ?
                """,
                resultSet -> resultSet.next() ? resultSet.getObject(1, LocalDate.class) : null,
                indexCode);
        return Optional.ofNullable(date);
    }

    private static class MarketIndexQuoteRowMapper implements RowMapper<MarketIndexQuote> {

        @Override
        public MarketIndexQuote mapRow(ResultSet resultSet, int rowNum) throws SQLException {
            return new MarketIndexQuote(
                    resultSet.getString("index_code"),
                    resultSet.getString("index_name"),
                    resultSet.getString("market"),
                    resultSet.getBigDecimal("current_value"),
                    resultSet.getString("change_sign"),
                    resultSet.getBigDecimal("change_value"),
                    resultSet.getBigDecimal("change_rate"),
                    resultSet.getLong("accumulated_volume"),
                    resultSet.getLong("accumulated_trading_value"),
                    resultSet.getBigDecimal("open_value"),
                    resultSet.getBigDecimal("high_value"),
                    resultSet.getBigDecimal("low_value"),
                    resultSet.getTimestamp("market_data_time").toInstant(),
                    resultSet.getString("source"));
        }
    }

    private static class MarketIndexIntradayPriceRowMapper implements RowMapper<MarketIndexIntradayPrice> {

        @Override
        public MarketIndexIntradayPrice mapRow(ResultSet resultSet, int rowNum) throws SQLException {
            return new MarketIndexIntradayPrice(
                    resultSet.getString("index_code"),
                    resultSet.getString("index_name"),
                    resultSet.getString("market"),
                    resultSet.getTimestamp("bucket_start").toLocalDateTime(),
                    resultSet.getBigDecimal("open_value"),
                    resultSet.getBigDecimal("high_value"),
                    resultSet.getBigDecimal("low_value"),
                    resultSet.getBigDecimal("close_value"),
                    resultSet.getLong("trading_volume"),
                    resultSet.getBigDecimal("trading_value_krw"),
                    resultSet.getString("source"),
                    resultSet.getTimestamp("collected_at").toInstant());
        }
    }
}

package com.hana.omniconnect.market.infra;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.hana.omniconnect.market.application.MarketIntradayPriceRepository;
import com.hana.omniconnect.market.domain.MarketIntradayPrice;
import com.hana.omniconnect.market.domain.MarketIntradayRealtimeTick;

@Repository
public class JdbcMarketIntradayPriceRepository implements MarketIntradayPriceRepository {

    private static final RowMapper<MarketIntradayPrice> ROW_MAPPER = new MarketIntradayPriceRowMapper();
    private static final String POSTGRESQL_UPSERT_SQL = """
            INSERT INTO market_intraday_minute_price (
                stock_code, bucket_start, trade_date, market,
                open_price_krw, high_price_krw, low_price_krw, close_price_krw,
                trading_volume, trading_value_krw, source, collected_at, updated_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
            ON CONFLICT (stock_code, bucket_start) DO UPDATE SET
                trade_date = EXCLUDED.trade_date,
                market = EXCLUDED.market,
                open_price_krw = EXCLUDED.open_price_krw,
                high_price_krw = EXCLUDED.high_price_krw,
                low_price_krw = EXCLUDED.low_price_krw,
                close_price_krw = EXCLUDED.close_price_krw,
                trading_volume = EXCLUDED.trading_volume,
                trading_value_krw = EXCLUDED.trading_value_krw,
                source = EXCLUDED.source,
                collected_at = EXCLUDED.collected_at,
                updated_at = CURRENT_TIMESTAMP
            """;
    private static final String H2_UPSERT_SQL = """
            MERGE INTO market_intraday_minute_price (
                stock_code, bucket_start, trade_date, market,
                open_price_krw, high_price_krw, low_price_krw, close_price_krw,
                trading_volume, trading_value_krw, source, collected_at, updated_at
            ) KEY (stock_code, bucket_start)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
            """;

    private final JdbcTemplate jdbcTemplate;
    private final String upsertSql;

    public JdbcMarketIntradayPriceRepository(JdbcTemplate jdbcTemplate, DataSource dataSource) {
        this.jdbcTemplate = jdbcTemplate;
        this.upsertSql = resolveUpsertSql(dataSource);
    }

    @Override
    @Transactional
    public int upsertAll(List<MarketIntradayPrice> prices) {
        if (prices == null || prices.isEmpty()) {
            return 0;
        }
        for (MarketIntradayPrice price : prices) {
            jdbcTemplate.update(
                    upsertSql,
                    price.stockCode(),
                    Timestamp.valueOf(price.bucketStart()),
                    price.bucketStart().toLocalDate(),
                    price.market(),
                    price.openPriceKrw(),
                    price.highPriceKrw(),
                    price.lowPriceKrw(),
                    price.closePriceKrw(),
                    price.tradingVolume(),
                    price.tradingValueKrw(),
                    price.source(),
                    Timestamp.from(price.collectedAt()));
        }
        return prices.size();
    }

    private static String resolveUpsertSql(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            return switch (metadata.getDatabaseProductName()) {
                case "PostgreSQL" -> POSTGRESQL_UPSERT_SQL;
                case "H2" -> H2_UPSERT_SQL;
                default -> throw new IllegalStateException(
                        "Unsupported market price database: " + metadata.getDatabaseProductName());
            };
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to detect market price database", exception);
        }
    }

    @Override
    @Transactional
    public void recordRealtimeTick(MarketIntradayRealtimeTick tick) {
        int updated = jdbcTemplate.update(
                """
                UPDATE market_intraday_minute_price
                SET high_price_krw = CASE WHEN high_price_krw >= ? THEN high_price_krw ELSE ? END,
                    low_price_krw = CASE WHEN low_price_krw <= ? THEN low_price_krw ELSE ? END,
                    close_price_krw = ?,
                    trading_volume = trading_volume + ?,
                    trading_value_krw = trading_value_krw + ?,
                    source = ?,
                    collected_at = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE stock_code = ?
                  AND bucket_start = ?
                """,
                tick.priceKrw(),
                tick.priceKrw(),
                tick.priceKrw(),
                tick.priceKrw(),
                tick.priceKrw(),
                tick.executionVolume(),
                tick.tradingValueKrw(),
                tick.source(),
                Timestamp.from(tick.collectedAt()),
                tick.stockCode(),
                Timestamp.valueOf(tick.bucketStart()));
        if (updated == 0) {
            insertRealtimeTick(tick);
        }
    }

    private void insertRealtimeTick(MarketIntradayRealtimeTick tick) {
        jdbcTemplate.update(
                """
                INSERT INTO market_intraday_minute_price (
                    stock_code, bucket_start, trade_date, market,
                    open_price_krw, high_price_krw, low_price_krw, close_price_krw,
                    trading_volume, trading_value_krw, source, collected_at, updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """,
                tick.stockCode(),
                Timestamp.valueOf(tick.bucketStart()),
                tick.bucketStart().toLocalDate(),
                tick.market(),
                tick.priceKrw(),
                tick.priceKrw(),
                tick.priceKrw(),
                tick.priceKrw(),
                tick.executionVolume(),
                tick.tradingValueKrw(),
                tick.source(),
                Timestamp.from(tick.collectedAt()));
    }

    @Override
    public Optional<MarketIntradayPrice> findLatestByStockCodeAndDate(String stockCode, LocalDate date) {
        return jdbcTemplate.query(
                        """
                        SELECT stock_code, bucket_start, market,
                               open_price_krw, high_price_krw, low_price_krw, close_price_krw,
                               trading_volume, trading_value_krw, source, collected_at
                        FROM market_intraday_minute_price
                        WHERE stock_code = ?
                          AND trade_date = ?
                        ORDER BY bucket_start DESC
                        LIMIT 1
                        """,
                        ROW_MAPPER,
                        stockCode,
                        date)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<MarketIntradayPrice> findLatestByStockCodeAtOrBefore(String stockCode, LocalDate date) {
        return jdbcTemplate.query(
                        """
                        SELECT stock_code, bucket_start, market,
                               open_price_krw, high_price_krw, low_price_krw, close_price_krw,
                               trading_volume, trading_value_krw, source, collected_at
                        FROM market_intraday_minute_price
                        WHERE stock_code = ?
                          AND trade_date <= ?
                        ORDER BY bucket_start DESC
                        LIMIT 1
                        """,
                        ROW_MAPPER,
                        stockCode,
                        date)
                .stream()
                .findFirst();
    }

    @Override
    public long sumTradingVolumeByStockCodeAndDate(String stockCode, LocalDate date) {
        Long volume = jdbcTemplate.queryForObject(
                """
                SELECT COALESCE(SUM(trading_volume), 0)
                FROM market_intraday_minute_price
                WHERE stock_code = ?
                  AND trade_date = ?
                """,
                Long.class,
                stockCode,
                date);
        return volume == null ? 0L : Math.max(volume, 0L);
    }

    @Override
    public List<MarketIntradayPrice> findByStockCodeAndDate(String stockCode, LocalDate date, int limit) {
        return jdbcTemplate.query(
                """
                SELECT stock_code, bucket_start, market,
                       open_price_krw, high_price_krw, low_price_krw, close_price_krw,
                       trading_volume, trading_value_krw, source, collected_at
                FROM market_intraday_minute_price
                WHERE stock_code = ?
                  AND trade_date = ?
                ORDER BY bucket_start ASC
                LIMIT ?
                """,
                ROW_MAPPER,
                stockCode,
                date,
                limit);
    }

    private static class MarketIntradayPriceRowMapper implements RowMapper<MarketIntradayPrice> {

        @Override
        public MarketIntradayPrice mapRow(ResultSet resultSet, int rowNum) throws SQLException {
            return new MarketIntradayPrice(
                    resultSet.getString("stock_code"),
                    resultSet.getTimestamp("bucket_start").toLocalDateTime(),
                    resultSet.getString("market"),
                    resultSet.getBigDecimal("open_price_krw"),
                    resultSet.getBigDecimal("high_price_krw"),
                    resultSet.getBigDecimal("low_price_krw"),
                    resultSet.getBigDecimal("close_price_krw"),
                    resultSet.getLong("trading_volume"),
                    resultSet.getBigDecimal("trading_value_krw"),
                    resultSet.getString("source"),
                    resultSet.getTimestamp("collected_at").toInstant());
        }
    }
}

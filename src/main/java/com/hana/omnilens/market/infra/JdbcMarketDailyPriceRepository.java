package com.hana.omnilens.market.infra;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.hana.omnilens.market.application.MarketDailyPriceRepository;
import com.hana.omnilens.market.domain.MarketDailyPrice;

@Repository
public class JdbcMarketDailyPriceRepository implements MarketDailyPriceRepository {

    private static final RowMapper<MarketDailyPrice> ROW_MAPPER = new MarketDailyPriceRowMapper();

    private final JdbcTemplate jdbcTemplate;

    public JdbcMarketDailyPriceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public int upsertAll(List<MarketDailyPrice> prices) {
        if (prices == null || prices.isEmpty()) {
            return 0;
        }
        for (MarketDailyPrice price : prices) {
            int updated = jdbcTemplate.update(
                    """
                    UPDATE market_daily_price
                    SET market = ?,
                        open_price_krw = ?,
                        high_price_krw = ?,
                        low_price_krw = ?,
                        close_price_krw = ?,
                        change_rate = ?,
                        trading_volume = ?,
                        trading_value_krw = ?,
                        adjusted_close_price_krw = ?,
                        source = ?,
                        collected_at = ?,
                        updated_at = CURRENT_TIMESTAMP
                    WHERE stock_code = ?
                      AND trade_date = ?
                    """,
                    price.market(),
                    price.openPriceKrw(),
                    price.highPriceKrw(),
                    price.lowPriceKrw(),
                    price.closePriceKrw(),
                    price.changeRate(),
                    price.tradingVolume(),
                    price.tradingValueKrw(),
                    price.adjustedClosePriceKrw(),
                    price.source(),
                    Timestamp.from(price.collectedAt()),
                    price.stockCode(),
                    price.tradeDate());
            if (updated == 0) {
                insert(price);
            }
        }
        return prices.size();
    }

    private void insert(MarketDailyPrice price) {
        jdbcTemplate.update(
                """
                INSERT INTO market_daily_price (
                    stock_code, trade_date, market,
                    open_price_krw, high_price_krw, low_price_krw, close_price_krw,
                    change_rate, trading_volume, trading_value_krw, adjusted_close_price_krw,
                    source, collected_at, updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """,
                price.stockCode(),
                price.tradeDate(),
                price.market(),
                price.openPriceKrw(),
                price.highPriceKrw(),
                price.lowPriceKrw(),
                price.closePriceKrw(),
                price.changeRate(),
                price.tradingVolume(),
                price.tradingValueKrw(),
                price.adjustedClosePriceKrw(),
                price.source(),
                Timestamp.from(price.collectedAt()));
    }

    @Override
    public List<MarketDailyPrice> findByStockCode(String stockCode, LocalDate from, LocalDate to, int limit) {
        return jdbcTemplate.query(
                """
                SELECT stock_code, trade_date, market,
                       open_price_krw, high_price_krw, low_price_krw, close_price_krw,
                       change_rate, trading_volume, trading_value_krw, adjusted_close_price_krw,
                       source, collected_at
                FROM market_daily_price
                WHERE stock_code = ?
                  AND trade_date BETWEEN ? AND ?
                ORDER BY trade_date ASC
                LIMIT ?
                """,
                ROW_MAPPER,
                stockCode,
                from,
                to,
                limit);
    }

    @Override
    public List<LocalDate> findTradingDates(LocalDate from, LocalDate to) {
        return jdbcTemplate.query(
                """
                SELECT DISTINCT trade_date
                FROM market_daily_price
                WHERE trade_date BETWEEN ? AND ?
                ORDER BY trade_date ASC
                """,
                (resultSet, rowNum) -> resultSet.getObject("trade_date", LocalDate.class),
                from,
                to);
    }

    private static class MarketDailyPriceRowMapper implements RowMapper<MarketDailyPrice> {

        @Override
        public MarketDailyPrice mapRow(ResultSet resultSet, int rowNum) throws SQLException {
            return new MarketDailyPrice(
                    resultSet.getString("stock_code"),
                    resultSet.getObject("trade_date", LocalDate.class),
                    resultSet.getString("market"),
                    resultSet.getBigDecimal("open_price_krw"),
                    resultSet.getBigDecimal("high_price_krw"),
                    resultSet.getBigDecimal("low_price_krw"),
                    resultSet.getBigDecimal("close_price_krw"),
                    resultSet.getBigDecimal("change_rate"),
                    resultSet.getLong("trading_volume"),
                    resultSet.getBigDecimal("trading_value_krw"),
                    resultSet.getBigDecimal("adjusted_close_price_krw"),
                    resultSet.getString("source"),
                    resultSet.getTimestamp("collected_at").toInstant());
        }
    }
}

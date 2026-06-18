package com.hana.omnilens.market.infra;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.hana.omnilens.market.application.StockMasterRepository;
import com.hana.omnilens.market.domain.StockSummary;

@Repository
public class JdbcStockMasterRepository implements StockMasterRepository {

    private static final int SEARCH_LIMIT = 50;
    private static final RowMapper<StockSummary> STOCK_ROW_MAPPER = new StockSummaryRowMapper();

    private final JdbcTemplate jdbcTemplate;

    public JdbcStockMasterRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<StockSummary> findByCode(String stockCode) {
        return jdbcTemplate.query(
                        """
                        SELECT stock_code, stock_name, stock_name_en, market, isin_code, dart_corp_code
                        FROM stock_master
                        WHERE stock_code = ?
                        """,
                        STOCK_ROW_MAPPER,
                        stockCode)
                .stream()
                .findFirst();
    }

    @Override
    public List<StockSummary> search(String query) {
        String likeQuery = "%" + query.toLowerCase(Locale.ROOT) + "%";
        return jdbcTemplate.query(
                """
                SELECT stock_code, stock_name, stock_name_en, market, isin_code, dart_corp_code
                FROM stock_master
                WHERE stock_code LIKE ?
                   OR LOWER(stock_name) LIKE ?
                   OR LOWER(stock_name_en) LIKE ?
                ORDER BY stock_code
                LIMIT ?
                """,
                STOCK_ROW_MAPPER,
                likeQuery,
                likeQuery,
                likeQuery,
                SEARCH_LIMIT);
    }

    int count() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM stock_master", Integer.class);
        return count == null ? 0 : count;
    }

    void insertAll(List<StockSummary> stocks) {
        jdbcTemplate.batchUpdate(
                """
                INSERT INTO stock_master (
                    stock_code, stock_name, stock_name_en, market, isin_code, dart_corp_code
                )
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                stocks,
                100,
                (statement, stock) -> {
                    statement.setString(1, stock.stockCode());
                    statement.setString(2, stock.stockName());
                    statement.setString(3, stock.stockNameEn());
                    statement.setString(4, stock.market());
                    statement.setString(5, stock.isinCode());
                    statement.setString(6, stock.dartCorpCode());
                });
    }

    private static class StockSummaryRowMapper implements RowMapper<StockSummary> {

        @Override
        public StockSummary mapRow(ResultSet resultSet, int rowNum) throws SQLException {
            return new StockSummary(
                    resultSet.getString("stock_code"),
                    resultSet.getString("stock_name"),
                    resultSet.getString("stock_name_en"),
                    resultSet.getString("market"),
                    resultSet.getString("isin_code"),
                    resultSet.getString("dart_corp_code"));
        }
    }
}

package com.hana.omnilens.market.infra;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

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
                          AND active = TRUE
                        """,
                        STOCK_ROW_MAPPER,
                        stockCode)
                .stream()
                .findFirst();
    }

    @Override
    public List<StockSummary> findAll(int limit) {
        return jdbcTemplate.query(
                """
                SELECT stock_master.stock_code,
                       stock_master.stock_name,
                       stock_master.stock_name_en,
                       stock_master.market,
                       stock_master.isin_code,
                       stock_master.dart_corp_code
                FROM stock_master
                LEFT JOIN stock_master_priority
                    ON stock_master.stock_code = stock_master_priority.stock_code
                WHERE stock_master.active = TRUE
                ORDER BY stock_master_priority.priority_rank NULLS LAST, stock_master.stock_code
                LIMIT ?
                """,
                STOCK_ROW_MAPPER,
                limit);
    }

    @Override
    public List<StockSummary> search(String query) {
        String likeQuery = "%" + query.toLowerCase(Locale.ROOT) + "%";
        return jdbcTemplate.query(
                """
                SELECT stock_code, stock_name, stock_name_en, market, isin_code, dart_corp_code
                FROM stock_master
                WHERE active = TRUE
                  AND (stock_code LIKE ?
                   OR LOWER(stock_name) LIKE ?
                   OR LOWER(stock_name_en) LIKE ?)
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
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM stock_master WHERE active = TRUE",
                Integer.class);
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

    void upsertAll(List<StockSummary> stocks) {
        for (StockSummary stock : stocks) {
            Optional<StockSummary> existing = findAnyByCode(stock.stockCode());
            if (existing.isPresent()) {
                update(stock, existing.get());
            } else {
                insertAll(List.of(stock));
            }
        }
    }

    @Transactional
    void reconcileMarketSnapshot(String market, List<StockSummary> stocks) {
        if (stocks.isEmpty() || stocks.stream().anyMatch(stock -> !market.equals(stock.market()))) {
            throw new IllegalArgumentException("stock master snapshot must be non-empty and single-market");
        }
        jdbcTemplate.update(
                "UPDATE stock_master SET active = FALSE WHERE market = ?",
                market);
        upsertAll(stocks);
    }

    int updateDartCorpCodes(Map<String, String> corpCodeByStockCode) {
        if (corpCodeByStockCode == null || corpCodeByStockCode.isEmpty()) {
            return 0;
        }
        int updated = 0;
        for (Map.Entry<String, String> entry : corpCodeByStockCode.entrySet()) {
            updated += jdbcTemplate.update(
                    """
                    UPDATE stock_master
                    SET dart_corp_code = ?
                    WHERE stock_code = ?
                      AND (dart_corp_code IS NULL OR dart_corp_code = '')
                    """,
                    entry.getValue(),
                    entry.getKey());
        }
        return updated;
    }

    int updatePreferredShareDartCorpCodesFromCommonShares() {
        Integer updated = jdbcTemplate.update(
                """
                UPDATE stock_master preferred
                SET dart_corp_code = common.dart_corp_code
                FROM stock_master common
                WHERE (preferred.dart_corp_code IS NULL OR preferred.dart_corp_code = '')
                  AND preferred.stock_name ~ '우$'
                  AND common.stock_name = regexp_replace(preferred.stock_name, '우$', '')
                  AND common.dart_corp_code IS NOT NULL
                  AND common.dart_corp_code <> ''
                """);
        return updated == null ? 0 : updated;
    }

    private Optional<StockSummary> findAnyByCode(String stockCode) {
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

    private void update(StockSummary stock, StockSummary existing) {
        String stockNameEn = stock.stockNameEn().equals(stock.stockName())
                && !existing.stockNameEn().equals(existing.stockName())
                ? existing.stockNameEn()
                : stock.stockNameEn();
        String dartCorpCode = stock.dartCorpCode().isBlank()
                ? existing.dartCorpCode()
                : stock.dartCorpCode();

        jdbcTemplate.update(
                """
                UPDATE stock_master
                SET stock_name = ?,
                    stock_name_en = ?,
                    market = ?,
                    isin_code = ?,
                    dart_corp_code = ?,
                    active = TRUE,
                    master_synced_at = CURRENT_TIMESTAMP
                WHERE stock_code = ?
                """,
                stock.stockName(),
                stockNameEn,
                stock.market(),
                stock.isinCode(),
                dartCorpCode,
                stock.stockCode());
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

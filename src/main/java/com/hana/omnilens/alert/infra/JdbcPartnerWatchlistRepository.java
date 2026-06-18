package com.hana.omnilens.alert.infra;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.hana.omnilens.alert.application.PartnerWatchlist;
import com.hana.omnilens.alert.application.PartnerWatchlistRepository;

@Repository
public class JdbcPartnerWatchlistRepository implements PartnerWatchlistRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcPartnerWatchlistRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<PartnerWatchlist> findAll() {
        List<WatchlistRow> rows = jdbcTemplate.query(
                """
                SELECT partner_id, stock_code
                FROM partner_watchlist_subscription
                ORDER BY partner_id, sort_order, stock_code
                """,
                (resultSet, rowNum) -> new WatchlistRow(
                        resultSet.getString("partner_id"),
                        resultSet.getString("stock_code")));
        Map<String, List<String>> groupedRows = new LinkedHashMap<>();
        for (WatchlistRow row : rows) {
            groupedRows.computeIfAbsent(row.partnerId(), ignored -> new ArrayList<>()).add(row.stockCode());
        }
        return groupedRows.entrySet().stream()
                .map(entry -> new PartnerWatchlist(entry.getKey(), entry.getValue()))
                .toList();
    }

    @Override
    public List<String> findStockCodes(String partnerId) {
        return jdbcTemplate.queryForList(
                """
                SELECT stock_code
                FROM partner_watchlist_subscription
                WHERE partner_id = ?
                ORDER BY sort_order, stock_code
                """,
                String.class,
                partnerId);
    }

    @Override
    @Transactional
    public List<String> replace(String partnerId, List<String> stockCodes) {
        jdbcTemplate.update(
                "DELETE FROM partner_watchlist_subscription WHERE partner_id = ?",
                partnerId);
        if (!stockCodes.isEmpty()) {
            jdbcTemplate.batchUpdate(
                    """
                    INSERT INTO partner_watchlist_subscription (partner_id, stock_code, sort_order)
                    VALUES (?, ?, ?)
                    """,
                    indexedStockCodes(stockCodes),
                    100,
                    (statement, stockCode) -> {
                        statement.setString(1, partnerId);
                        statement.setString(2, stockCode.stockCode());
                        statement.setInt(3, stockCode.sortOrder());
                    });
        }
        return findStockCodes(partnerId);
    }

    private static List<IndexedStockCode> indexedStockCodes(List<String> stockCodes) {
        List<IndexedStockCode> indexedStockCodes = new ArrayList<>(stockCodes.size());
        for (int index = 0; index < stockCodes.size(); index++) {
            indexedStockCodes.add(new IndexedStockCode(stockCodes.get(index), index));
        }
        return indexedStockCodes;
    }

    private record WatchlistRow(String partnerId, String stockCode) {
    }

    private record IndexedStockCode(String stockCode, int sortOrder) {
    }
}

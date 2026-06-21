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

import com.hana.omnilens.market.application.ForeignOwnershipDailySnapshotRepository;
import com.hana.omnilens.market.domain.ForeignOwnershipDailySnapshot;

@Repository
public class JdbcForeignOwnershipDailySnapshotRepository implements ForeignOwnershipDailySnapshotRepository {

    private static final RowMapper<ForeignOwnershipDailySnapshot> ROW_MAPPER =
            new ForeignOwnershipDailySnapshotRowMapper();

    private final JdbcTemplate jdbcTemplate;

    public JdbcForeignOwnershipDailySnapshotRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public int upsert(ForeignOwnershipDailySnapshot snapshot) {
        int updated = jdbcTemplate.update(
                """
                UPDATE foreign_ownership_daily_snapshot
                SET foreign_owned_quantity = ?,
                    foreign_ownership_rate = ?,
                    foreign_limit_quantity = ?,
                    foreign_limit_exhaustion_rate = ?,
                    source = ?,
                    collected_at = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE stock_code = ?
                  AND base_date = ?
                """,
                snapshot.foreignOwnedQuantity(),
                snapshot.foreignOwnershipRate(),
                snapshot.foreignLimitQuantity(),
                snapshot.foreignLimitExhaustionRate(),
                snapshot.source(),
                Timestamp.from(snapshot.collectedAt()),
                snapshot.stockCode(),
                snapshot.baseDate());
        if (updated > 0) {
            return updated;
        }
        return jdbcTemplate.update(
                """
                INSERT INTO foreign_ownership_daily_snapshot (
                    stock_code, base_date, foreign_owned_quantity, foreign_ownership_rate,
                    foreign_limit_quantity, foreign_limit_exhaustion_rate, source, collected_at, updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """,
                snapshot.stockCode(),
                snapshot.baseDate(),
                snapshot.foreignOwnedQuantity(),
                snapshot.foreignOwnershipRate(),
                snapshot.foreignLimitQuantity(),
                snapshot.foreignLimitExhaustionRate(),
                snapshot.source(),
                Timestamp.from(snapshot.collectedAt()));
    }

    @Override
    public List<ForeignOwnershipDailySnapshot> findRecent(String stockCode, LocalDate to, int limit) {
        return jdbcTemplate.query(
                """
                SELECT stock_code, base_date, foreign_owned_quantity, foreign_ownership_rate,
                       foreign_limit_quantity, foreign_limit_exhaustion_rate, source, collected_at
                FROM (
                    SELECT stock_code, base_date, foreign_owned_quantity, foreign_ownership_rate,
                           foreign_limit_quantity, foreign_limit_exhaustion_rate, source, collected_at
                    FROM foreign_ownership_daily_snapshot
                    WHERE stock_code = ?
                      AND base_date <= ?
                    ORDER BY base_date DESC
                    LIMIT ?
                ) recent
                ORDER BY base_date ASC
                """,
                ROW_MAPPER,
                stockCode,
                to,
                limit);
    }

    private static class ForeignOwnershipDailySnapshotRowMapper
            implements RowMapper<ForeignOwnershipDailySnapshot> {

        @Override
        public ForeignOwnershipDailySnapshot mapRow(ResultSet resultSet, int rowNum) throws SQLException {
            return new ForeignOwnershipDailySnapshot(
                    resultSet.getString("stock_code"),
                    resultSet.getObject("base_date", LocalDate.class),
                    resultSet.getLong("foreign_owned_quantity"),
                    resultSet.getBigDecimal("foreign_ownership_rate"),
                    resultSet.getLong("foreign_limit_quantity"),
                    resultSet.getBigDecimal("foreign_limit_exhaustion_rate"),
                    resultSet.getString("source"),
                    resultSet.getTimestamp("collected_at").toInstant());
        }
    }
}

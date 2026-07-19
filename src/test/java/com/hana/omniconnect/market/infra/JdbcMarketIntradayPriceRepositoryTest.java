package com.hana.omniconnect.market.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import com.hana.omniconnect.market.domain.MarketIntradayPrice;

class JdbcMarketIntradayPriceRepositoryTest {

    @Test
    void upsertAllUsesPostgresqlConflictUpdate() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        org.mockito.Mockito.when(dataSource.getConnection()).thenReturn(connection);
        org.mockito.Mockito.when(connection.getMetaData()).thenReturn(metadata);
        org.mockito.Mockito.when(metadata.getDatabaseProductName()).thenReturn("PostgreSQL");
        JdbcMarketIntradayPriceRepository repository = new JdbcMarketIntradayPriceRepository(jdbcTemplate, dataSource);
        MarketIntradayPrice price = new MarketIntradayPrice(
                "030200",
                LocalDateTime.of(2026, 7, 13, 9, 1),
                "KOSPI",
                new BigDecimal("52000"),
                new BigDecimal("52100"),
                new BigDecimal("51900"),
                new BigDecimal("52050"),
                1_200L,
                new BigDecimal("62460000"),
                "KIS_TIME_ITEM_CHART_PRICE",
                Instant.parse("2026-07-13T00:01:30Z"));

        int savedCount = repository.upsertAll(List.of(price));

        assertThat(savedCount).isEqualTo(1);
        verify(jdbcTemplate).update(
                contains("ON CONFLICT (stock_code, bucket_start) DO UPDATE"),
                eq("030200"),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                eq("KOSPI"),
                eq(new BigDecimal("52000")),
                eq(new BigDecimal("52100")),
                eq(new BigDecimal("51900")),
                eq(new BigDecimal("52050")),
                eq(1_200L),
                eq(new BigDecimal("62460000")),
                eq("KIS_TIME_ITEM_CHART_PRICE"),
                org.mockito.ArgumentMatchers.any());
    }
}

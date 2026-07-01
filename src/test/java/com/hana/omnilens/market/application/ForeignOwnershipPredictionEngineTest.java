package com.hana.omnilens.market.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.hana.omnilens.market.domain.ForeignOwnershipDailySnapshot;
import com.hana.omnilens.market.domain.ForeignOwnershipPrediction;
import com.hana.omnilens.provider.market.KisRealtimeTradeTick;
import com.hana.omnilens.provider.market.ForeignOwnershipSnapshot;

class ForeignOwnershipPredictionEngineTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-06-19T06:00:00Z");
    private final ForeignOwnershipPredictionEngine engine =
            new ForeignOwnershipPredictionEngine(Clock.fixed(FIXED_NOW, ZoneOffset.UTC));

    @Test
    void predictsSnapshotOnlyBoundaryWithoutOrderImpact() {
        ForeignOwnershipPrediction prediction = engine.predict(
                "BUY",
                20,
                Optional.of(snapshot()),
                Optional.empty());

        assertThat(prediction.orderImpactRate()).isEqualByComparingTo("0.000000");
        assertThat(prediction.intradayUncertaintyRate()).isEqualByComparingTo("0.050000");
        assertThat(prediction.minForeignLimitExhaustionRate()).isEqualByComparingTo("98.950000");
        assertThat(prediction.baseForeignLimitExhaustionRate()).isEqualByComparingTo("99.000000");
        assertThat(prediction.maxForeignLimitExhaustionRate()).isEqualByComparingTo("99.050000");
        assertThat(prediction.confidenceLevel()).isEqualTo("SNAPSHOT_ONLY");
        assertThat(prediction.confidenceScore()).isEqualByComparingTo("0.4500");
        assertThat(prediction.modelVersion()).isEqualTo("foreign-ownership-timeseries-v1");
        assertThat(prediction.historyObservationCount()).isZero();
        assertThat(prediction.baseDate()).isEqualTo(LocalDate.of(2025, 6, 3));
        assertThat(prediction.calculatedAt()).isEqualTo(FIXED_NOW);
    }

    @Test
    void ignoresRealtimeVolumeForForeignOwnershipLimitForecast() {
        ForeignOwnershipPrediction prediction = engine.predict(
                "BUY",
                10,
                Optional.of(snapshot()),
                Optional.of(new KisRealtimeTradeTick(
                        "005930",
                        "093000",
                        new BigDecimal("81500"),
                        new BigDecimal("1.25"),
                        new BigDecimal("81600"),
                        new BigDecimal("81400"),
                        1200L,
                        500L,
                        LocalDate.of(2025, 6, 4))));

        assertThat(prediction.orderImpactRate()).isEqualByComparingTo("0.000000");
        assertThat(prediction.intradayUncertaintyRate()).isEqualByComparingTo("0.050000");
        assertThat(prediction.minForeignLimitExhaustionRate()).isEqualByComparingTo("98.950000");
        assertThat(prediction.baseForeignLimitExhaustionRate()).isEqualByComparingTo("99.000000");
        assertThat(prediction.maxForeignLimitExhaustionRate()).isEqualByComparingTo("99.050000");
        assertThat(prediction.observedIntradayVolume()).isZero();
        assertThat(prediction.confidenceLevel()).isEqualTo("SNAPSHOT_ONLY");
        assertThat(prediction.confidenceScore()).isEqualByComparingTo("0.4500");
        assertThat(prediction.source()).isEqualTo("KRX_FOREIGN_OWNERSHIP_CACHE");
    }

    @Test
    void predictsTimeSeriesAdjustedBoundaryWithTrendAndConfidence() {
        ForeignOwnershipPrediction prediction = engine.predict(
                "BUY",
                1,
                Optional.of(snapshot()),
                Optional.empty(),
                List.of(
                        history(LocalDate.of(2025, 5, 30), "98.0000"),
                        history(LocalDate.of(2025, 5, 31), "98.4000"),
                        history(LocalDate.of(2025, 6, 1), "98.7000"),
                        history(LocalDate.of(2025, 6, 2), "98.9000"),
                        history(LocalDate.of(2025, 6, 3), "99.0000")));

        assertThat(prediction.orderImpactRate()).isEqualByComparingTo("0.000000");
        assertThat(prediction.trendDailyChangeRate()).isEqualByComparingTo("0.250000");
        assertThat(prediction.intradayUncertaintyRate()).isEqualByComparingTo("0.250000");
        assertThat(prediction.baseForeignLimitExhaustionRate()).isEqualByComparingTo("99.250000");
        assertThat(prediction.minForeignLimitExhaustionRate()).isEqualByComparingTo("99.000000");
        assertThat(prediction.maxForeignLimitExhaustionRate()).isEqualByComparingTo("99.500000");
        assertThat(prediction.historyObservationCount()).isEqualTo(5);
        assertThat(prediction.historyWindowDays()).isEqualTo(4);
        assertThat(prediction.confidenceLevel()).isEqualTo("TIME_SERIES_ADJUSTED");
        assertThat(prediction.confidenceScore()).isEqualByComparingTo("0.7500");
        assertThat(prediction.source()).isEqualTo("KRX_FOREIGN_OWNERSHIP_CACHE+FOREIGN_OWNERSHIP_DAILY_TIMESERIES");
    }

    @Test
    void returnsNoSnapshotPredictionWhenForeignOwnershipCacheIsMissing() {
        ForeignOwnershipPrediction prediction = engine.predict(
                "BUY",
                10,
                Optional.empty(),
                Optional.empty());

        assertThat(prediction.minForeignLimitExhaustionRate()).isEqualByComparingTo("0.000000");
        assertThat(prediction.baseForeignLimitExhaustionRate()).isEqualByComparingTo("0.000000");
        assertThat(prediction.maxForeignLimitExhaustionRate()).isEqualByComparingTo("0.000000");
        assertThat(prediction.confidenceLevel()).isEqualTo("NO_SNAPSHOT");
        assertThat(prediction.confidenceScore()).isEqualByComparingTo("0.0000");
        assertThat(prediction.source()).isEqualTo("FOREIGN_OWNERSHIP_PREDICTOR_NO_SNAPSHOT");
    }

    private ForeignOwnershipSnapshot snapshot() {
        return new ForeignOwnershipSnapshot(
                "005930",
                990L,
                new BigDecimal("49.50"),
                1_000L,
                new BigDecimal("99.0000"),
                LocalDate.of(2025, 6, 3));
    }

    private ForeignOwnershipDailySnapshot history(LocalDate baseDate, String rate) {
        return new ForeignOwnershipDailySnapshot(
                "005930",
                baseDate,
                990L,
                new BigDecimal("49.50"),
                1_000L,
                new BigDecimal(rate),
                "KRX_DATA_MARKETPLACE_FOREIGN_OWNERSHIP",
                FIXED_NOW);
    }
}

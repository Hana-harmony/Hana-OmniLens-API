package com.hana.omnilens.market.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.hana.omnilens.market.domain.ForeignOwnershipPrediction;
import com.hana.omnilens.provider.market.KisRealtimeTradeTick;
import com.hana.omnilens.provider.market.KrxForeignOwnershipSnapshot;

class ForeignOwnershipPredictionEngineTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-06-19T06:00:00Z");
    private final ForeignOwnershipPredictionEngine engine =
            new ForeignOwnershipPredictionEngine(Clock.fixed(FIXED_NOW, ZoneOffset.UTC));

    @Test
    void predictsSnapshotOnlyMinBaseMaxBoundaryWithBuyOrderImpact() {
        ForeignOwnershipPrediction prediction = engine.predict(
                "BUY",
                20,
                Optional.of(snapshot()),
                Optional.empty());

        assertThat(prediction.orderImpactRate()).isEqualByComparingTo("2.000000");
        assertThat(prediction.intradayUncertaintyRate()).isEqualByComparingTo("0.050000");
        assertThat(prediction.minForeignLimitExhaustionRate()).isEqualByComparingTo("100.950000");
        assertThat(prediction.baseForeignLimitExhaustionRate()).isEqualByComparingTo("101.000000");
        assertThat(prediction.maxForeignLimitExhaustionRate()).isEqualByComparingTo("101.050000");
        assertThat(prediction.confidenceLevel()).isEqualTo("SNAPSHOT_ONLY");
        assertThat(prediction.baseDate()).isEqualTo(LocalDate.of(2025, 6, 3));
        assertThat(prediction.calculatedAt()).isEqualTo(FIXED_NOW);
    }

    @Test
    void predictsRealtimeVolumeAdjustedBoundaryWithCappedUncertainty() {
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

        assertThat(prediction.orderImpactRate()).isEqualByComparingTo("1.000000");
        assertThat(prediction.intradayUncertaintyRate()).isEqualByComparingTo("0.500000");
        assertThat(prediction.minForeignLimitExhaustionRate()).isEqualByComparingTo("99.500000");
        assertThat(prediction.baseForeignLimitExhaustionRate()).isEqualByComparingTo("100.000000");
        assertThat(prediction.maxForeignLimitExhaustionRate()).isEqualByComparingTo("100.500000");
        assertThat(prediction.observedIntradayVolume()).isEqualTo(500L);
        assertThat(prediction.confidenceLevel()).isEqualTo("REALTIME_VOLUME_ADJUSTED");
        assertThat(prediction.source()).isEqualTo("KRX_FOREIGN_OWNERSHIP_CACHE+KIS_WEBSOCKET_TRADE_VOLUME");
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
        assertThat(prediction.source()).isEqualTo("FOREIGN_OWNERSHIP_PREDICTOR_NO_SNAPSHOT");
    }

    private KrxForeignOwnershipSnapshot snapshot() {
        return new KrxForeignOwnershipSnapshot(
                "005930",
                990L,
                new BigDecimal("49.50"),
                1_000L,
                new BigDecimal("99.0000"),
                LocalDate.of(2025, 6, 3));
    }
}

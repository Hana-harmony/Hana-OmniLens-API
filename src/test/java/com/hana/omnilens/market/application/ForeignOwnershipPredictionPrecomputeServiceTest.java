package com.hana.omnilens.market.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.hana.omnilens.config.ForeignOwnershipPredictionPrecomputeProperties;
import com.hana.omnilens.market.domain.ForeignOwnershipDailySnapshot;
import com.hana.omnilens.provider.ai.HannahAiForeignOwnershipPredictionClient;
import com.hana.omnilens.provider.ai.HannahAiForeignOwnershipPredictionResponse;
import com.hana.omnilens.provider.market.ForeignOwnershipSnapshot;

class ForeignOwnershipPredictionPrecomputeServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2025-06-04T00:00:00Z"),
            ZoneOffset.UTC);

    @Test
    void precomputesRestrictedUniversePredictionsIntoCache() {
        ForeignOwnershipSnapshotCache snapshotCache = new InMemoryForeignOwnershipSnapshotCache();
        InMemoryForeignOwnershipDailySnapshotRepository dailySnapshotRepository =
                new InMemoryForeignOwnershipDailySnapshotRepository();
        ForeignOwnershipPredictionCache predictionCache =
                new InMemoryForeignOwnershipPredictionCache();
        HannahAiForeignOwnershipPredictionClient hannahClient =
                mock(HannahAiForeignOwnershipPredictionClient.class);
        snapshotCache.put(new ForeignOwnershipSnapshot(
                "015760",
                995L,
                new BigDecimal("49.75"),
                1_000L,
                new BigDecimal("99.5000"),
                LocalDate.of(2025, 6, 4)));
        dailySnapshotRepository.upsert(new ForeignOwnershipDailySnapshot(
                "015760",
                LocalDate.of(2025, 6, 4),
                995L,
                new BigDecimal("49.75"),
                1_000L,
                new BigDecimal("99.5000"),
                "KRX_DATA_MARKETPLACE_FOREIGN_OWNERSHIP",
                FIXED_CLOCK.instant()));
        when(hannahClient.predict(any())).thenReturn(new HannahAiForeignOwnershipPredictionResponse(
                "015760",
                995L,
                990L,
                1_000L,
                0L,
                1_000L,
                999L,
                1_001L,
                new BigDecimal("99.475000"),
                new BigDecimal("99.975000"),
                new BigDecimal("100.475000"),
                BigDecimal.ZERO.setScale(6),
                BigDecimal.ZERO.setScale(6),
                0L,
                BigDecimal.ZERO.setScale(6),
                1,
                0,
                LocalDate.of(2025, 6, 4),
                FIXED_CLOCK.instant(),
                "AI_FOREIGN_OWNED_QUANTITY_ML",
                new BigDecimal("0.8600"),
                "hannah-foreign-owned-quantity-ml-v1",
                "HANNAH_MONTANA_AI_FOREIGN_OWNED_QUANTITY_ML"));

        ForeignOwnershipPredictionPrecomputeResult result =
                new ForeignOwnershipPredictionPrecomputeService(
                        snapshotCache,
                        dailySnapshotRepository,
                        predictionCache,
                        hannahClient,
                        new ForeignOwnershipPredictionPrecomputeProperties(
                                true,
                                true,
                                1),
                        FIXED_CLOCK)
                        .precomputeRestrictedUniverse();

        assertThat(result.precomputedCount()).isEqualTo(1);
        assertThat(predictionCache.find("015760", LocalDate.of(2025, 6, 4))).isPresent();
        assertThat(predictionCache.find("015760", LocalDate.of(2025, 6, 4)).orElseThrow()
                .baseForeignLimitExhaustionRate()).isEqualByComparingTo("99.975000");
    }
}

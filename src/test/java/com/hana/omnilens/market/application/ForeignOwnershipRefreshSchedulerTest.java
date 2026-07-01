package com.hana.omnilens.market.application;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.hana.omnilens.config.ForeignOwnershipRefreshProperties;

class ForeignOwnershipRefreshSchedulerTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-06-21T01:00:00Z");
    private final ForeignOwnershipRefreshService refreshService = org.mockito.Mockito.mock(ForeignOwnershipRefreshService.class);
    private final ForeignOwnershipModelTrainingService modelTrainingService =
            org.mockito.Mockito.mock(ForeignOwnershipModelTrainingService.class);
    private final ForeignOwnershipPredictionPrecomputeService predictionPrecomputeService =
            org.mockito.Mockito.mock(ForeignOwnershipPredictionPrecomputeService.class);

    @Test
    void skipsRefreshWhenSchedulerIsDisabled() {
        ForeignOwnershipRefreshScheduler scheduler = new ForeignOwnershipRefreshScheduler(
                refreshService,
                new ForeignOwnershipRefreshProperties(false, 60_000L, 60_000L, null, 1_200L, 1, 400, 100, List.of("005930")),
                Clock.fixed(FIXED_NOW, ZoneOffset.UTC));

        scheduler.refreshConfiguredForeignOwnership();

        verify(refreshService, never()).backfillMissing(
                LocalDate.of(2025, 5, 15),
                LocalDate.of(2026, 6, 19),
                List.of("005930"),
                100,
                1_200L);
    }

    @Test
    void backfillsMissingWeekdaysBeforeMarketOpen() {
        when(refreshService.backfillMissing(
                LocalDate.of(2025, 5, 15),
                LocalDate.of(2026, 6, 19),
                List.of("005930", "000660"),
                2,
                1_200L))
                .thenReturn(new ForeignOwnershipBackfillResult(
                        LocalDate.of(2025, 5, 15),
                        LocalDate.of(2026, 6, 19),
                        2,
                        0,
                        0,
                        0,
                        "KRX_DATA_MARKETPLACE_FOREIGN_OWNERSHIP",
                        "EMPTY",
                        List.of()));
        ForeignOwnershipRefreshScheduler scheduler = new ForeignOwnershipRefreshScheduler(
                refreshService,
                new ForeignOwnershipRefreshProperties(true, 60_000L, 60_000L, null, 1_200L, 1, 400, 2, List.of("005930", "000660")),
                Clock.fixed(FIXED_NOW, ZoneOffset.UTC));

        scheduler.refreshConfiguredForeignOwnership();

        verify(refreshService).backfillMissing(
                LocalDate.of(2025, 5, 15),
                LocalDate.of(2026, 6, 19),
                List.of("005930", "000660"),
                2,
                1_200L);
    }

    @Test
    void triggersModelRetrainingAfterSavedBackfillRows() {
        ForeignOwnershipBackfillResult result = new ForeignOwnershipBackfillResult(
                LocalDate.of(2025, 5, 15),
                LocalDate.of(2026, 6, 19),
                2,
                1,
                1,
                0,
                "KRX_DATA_MARKETPLACE_FOREIGN_OWNERSHIP",
                "SUCCESS",
                List.of());
        when(refreshService.backfillMissing(
                LocalDate.of(2025, 5, 15),
                LocalDate.of(2026, 6, 19),
                List.of("005930"),
                1,
                1_200L))
                .thenReturn(result);
        ForeignOwnershipRefreshScheduler scheduler = new ForeignOwnershipRefreshScheduler(
                refreshService,
                new ForeignOwnershipRefreshProperties(true, 60_000L, 60_000L, null, 1_200L, 1, 400, 1, List.of("005930")),
                modelTrainingService,
                predictionPrecomputeService,
                Clock.fixed(FIXED_NOW, ZoneOffset.UTC));

        scheduler.refreshConfiguredForeignOwnership();

        verify(modelTrainingService).retrainAfterRefreshIfEnabled(result);
        verify(predictionPrecomputeService).precomputeAfterRefreshIfEnabled(result);
    }
}

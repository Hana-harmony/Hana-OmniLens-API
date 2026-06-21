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

    @Test
    void skipsRefreshWhenSchedulerIsDisabled() {
        ForeignOwnershipRefreshScheduler scheduler = new ForeignOwnershipRefreshScheduler(
                refreshService,
                new ForeignOwnershipRefreshProperties(false, 60_000L, 60_000L, 1_200L, 1, 100, List.of("005930")),
                Clock.fixed(FIXED_NOW, ZoneOffset.UTC));

        scheduler.refreshConfiguredForeignOwnership();

        verify(refreshService, never()).collect(LocalDate.of(2026, 6, 20), List.of("005930"), 100, 1_200L);
    }

    @Test
    void collectsConfiguredStocksWithBaseDateOffset() {
        when(refreshService.collect(LocalDate.of(2026, 6, 19), List.of("005930", "000660"), 2, 1_200L))
                .thenReturn(new ForeignOwnershipCollectionResult(
                        LocalDate.of(2026, 6, 19),
                        2,
                        2,
                        0,
                        "KIS_CURRENT_PRICE_FOREIGN_OWNERSHIP",
                        "SUCCESS",
                        List.of()));
        ForeignOwnershipRefreshScheduler scheduler = new ForeignOwnershipRefreshScheduler(
                refreshService,
                new ForeignOwnershipRefreshProperties(true, 60_000L, 60_000L, 1_200L, 2, 2, List.of("005930", "000660")),
                Clock.fixed(FIXED_NOW, ZoneOffset.UTC));

        scheduler.refreshConfiguredForeignOwnership();

        verify(refreshService).collect(LocalDate.of(2026, 6, 19), List.of("005930", "000660"), 2, 1_200L);
    }
}

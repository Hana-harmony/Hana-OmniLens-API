package com.hana.omniconnect.market.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.hana.omniconnect.config.ForeignOwnershipModelTrainingProperties;
import com.hana.omniconnect.market.domain.ForeignOwnershipDailySnapshot;
import com.hana.omniconnect.provider.ai.HannahAiForeignOwnershipRetrainClient;
import com.hana.omniconnect.provider.ai.HannahAiForeignOwnershipRetrainRequest;

class ForeignOwnershipModelTrainingServiceTest {

    private final ForeignOwnershipDailySnapshotRepository repository =
            org.mockito.Mockito.mock(ForeignOwnershipDailySnapshotRepository.class);
    private final HannahAiForeignOwnershipRetrainClient retrainClient =
            org.mockito.Mockito.mock(HannahAiForeignOwnershipRetrainClient.class);

    @Test
    void retrainAfterRefreshSkipsHannahCallWhenHistoryIsBelowMinimum() {
        when(repository.findAllByStockCodes(ForeignOwnershipRestrictedStockUniverse.stockCodes()))
                .thenReturn(List.of(snapshot("015760", LocalDate.of(2026, 6, 30))));
        ForeignOwnershipModelTrainingService service = new ForeignOwnershipModelTrainingService(
                repository,
                retrainClient,
                properties());

        service.retrainAfterRefreshIfEnabled(new ForeignOwnershipBackfillResult(
                LocalDate.of(2026, 6, 29),
                LocalDate.of(2026, 6, 30),
                2,
                1,
                1,
                0,
                "KRX_DATA_MARKETPLACE_FOREIGN_OWNERSHIP",
                "SUCCESS",
                List.of()));

        verify(retrainClient, never()).retrain(any(HannahAiForeignOwnershipRetrainRequest.class));
    }

    private ForeignOwnershipModelTrainingProperties properties() {
        return new ForeignOwnershipModelTrainingProperties(
                true,
                true,
                Duration.ofSeconds(2),
                Duration.ofMinutes(20),
                "",
                29,
                2_500,
                50_000,
                250_000);
    }

    private ForeignOwnershipDailySnapshot snapshot(String stockCode, LocalDate baseDate) {
        return new ForeignOwnershipDailySnapshot(
                stockCode,
                baseDate,
                1_000_000L,
                java.math.BigDecimal.TEN,
                2_000_000L,
                new java.math.BigDecimal("50.0000"),
                "KRX_DATA_MARKETPLACE_FOREIGN_OWNERSHIP",
                java.time.Instant.parse("2026-07-01T00:00:00Z"));
    }
}

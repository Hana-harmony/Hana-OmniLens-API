package com.hana.omnilens.market.application;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.hana.omnilens.config.ForeignOwnershipModelTrainingProperties;
import com.hana.omnilens.market.domain.ForeignOwnershipDailySnapshot;
import com.hana.omnilens.provider.ai.HannahAiForeignOwnershipRetrainClient;
import com.hana.omnilens.provider.ai.HannahAiForeignOwnershipRetrainRequest;
import com.hana.omnilens.provider.ai.HannahAiForeignOwnershipRetrainResponse;
import com.hana.omnilens.provider.ai.HannahAiForeignOwnershipTrainingPoint;

@Service
public class ForeignOwnershipModelTrainingService {

    private static final Logger log = LoggerFactory.getLogger(ForeignOwnershipModelTrainingService.class);

    private final ForeignOwnershipDailySnapshotRepository repository;
    private final HannahAiForeignOwnershipRetrainClient retrainClient;
    private final ForeignOwnershipModelTrainingProperties properties;

    public ForeignOwnershipModelTrainingService(
            ForeignOwnershipDailySnapshotRepository repository,
            HannahAiForeignOwnershipRetrainClient retrainClient,
            ForeignOwnershipModelTrainingProperties properties) {
        this.repository = repository;
        this.retrainClient = retrainClient;
        this.properties = properties;
    }

    public HannahAiForeignOwnershipRetrainResponse retrainRestrictedUniverse() {
        if (!properties.enabled()) {
            throw new IllegalStateException("Foreign ownership model training is disabled");
        }
        List<String> restrictedStockCodes = ForeignOwnershipRestrictedStockUniverse.stockCodes();
        List<ForeignOwnershipDailySnapshot> snapshots =
                repository.findAllByStockCodes(restrictedStockCodes);
        if (snapshots.isEmpty()) {
            throw new IllegalStateException("Foreign ownership training history is empty");
        }
        HannahAiForeignOwnershipRetrainResponse response = retrainClient.retrain(
                new HannahAiForeignOwnershipRetrainRequest(
                        snapshots.stream()
                                .map(this::toTrainingPoint)
                                .toList(),
                        restrictedStockCodes,
                        properties.minimumPromotableStockCount(),
                        properties.minimumPromotableHistoryDays(),
                        properties.minimumPromotableObservations(),
                        properties.maxModelTrainingSamples()));
        log.info(
                "Foreign ownership model retrain completed promoted={} releaseStatus={} observations={} stocks={} samples={} modelReloaded={}",
                response.promoted(),
                response.releaseStatus(),
                response.observationCount(),
                response.stockCount(),
                response.sampleCount(),
                response.modelReloaded());
        return response;
    }

    public void retrainAfterRefreshIfEnabled(ForeignOwnershipBackfillResult backfillResult) {
        if (!properties.enabled() || !properties.triggerAfterRefresh()) {
            return;
        }
        if (backfillResult.savedCount() <= 0) {
            return;
        }
        try {
            retrainRestrictedUniverse();
        } catch (RuntimeException exception) {
            log.warn(
                    "Foreign ownership model retrain failed after refresh savedCount={} status={}",
                    backfillResult.savedCount(),
                    backfillResult.status(),
                    exception);
        }
    }

    private HannahAiForeignOwnershipTrainingPoint toTrainingPoint(
            ForeignOwnershipDailySnapshot snapshot) {
        return new HannahAiForeignOwnershipTrainingPoint(
                snapshot.stockCode(),
                snapshot.baseDate(),
                snapshot.foreignOwnedQuantity(),
                snapshot.foreignLimitQuantity());
    }
}

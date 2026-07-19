package com.hana.omniconnect.market.application;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.hana.omniconnect.config.ForeignOwnershipModelTrainingProperties;
import com.hana.omniconnect.market.domain.ForeignOwnershipDailySnapshot;
import com.hana.omniconnect.provider.ai.HannahAiForeignOwnershipRetrainClient;
import com.hana.omniconnect.provider.ai.HannahAiForeignOwnershipRetrainRequest;
import com.hana.omniconnect.provider.ai.HannahAiForeignOwnershipRetrainResponse;
import com.hana.omniconnect.provider.ai.HannahAiForeignOwnershipTrainingPoint;

@Service
public class ForeignOwnershipModelTrainingService {

    private static final Logger log = LoggerFactory.getLogger(ForeignOwnershipModelTrainingService.class);
    private static final int MIN_HANNAH_RETRAIN_HISTORY_POINTS = 120;

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
        return retrainRestrictedUniverse(snapshots, restrictedStockCodes);
    }

    private HannahAiForeignOwnershipRetrainResponse retrainRestrictedUniverse(
            List<ForeignOwnershipDailySnapshot> snapshots,
            List<String> restrictedStockCodes) {
        if (snapshots.isEmpty()) {
            throw new IllegalStateException("Foreign ownership training history is empty");
        }
        if (snapshots.size() < MIN_HANNAH_RETRAIN_HISTORY_POINTS) {
            throw new IllegalStateException("Foreign ownership training history is below Hannah retrain minimum");
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
        List<String> restrictedStockCodes = ForeignOwnershipRestrictedStockUniverse.stockCodes();
        List<ForeignOwnershipDailySnapshot> snapshots =
                repository.findAllByStockCodes(restrictedStockCodes);
        if (snapshots.size() < MIN_HANNAH_RETRAIN_HISTORY_POINTS) {
            log.info(
                    "Foreign ownership model retrain skipped after refresh historyCount={} minimumHistoryCount={}",
                    snapshots.size(),
                    MIN_HANNAH_RETRAIN_HISTORY_POINTS);
            return;
        }
        try {
            retrainRestrictedUniverse(snapshots, restrictedStockCodes);
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

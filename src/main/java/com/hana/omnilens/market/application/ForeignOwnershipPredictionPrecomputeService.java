package com.hana.omnilens.market.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import com.hana.omnilens.config.ForeignOwnershipPredictionPrecomputeProperties;
import com.hana.omnilens.market.domain.ForeignOwnershipDailySnapshot;
import com.hana.omnilens.market.domain.ForeignOwnershipPrediction;
import com.hana.omnilens.provider.ProviderCircuitOpenException;
import com.hana.omnilens.provider.ai.HannahAiForeignOwnershipHistoryPoint;
import com.hana.omnilens.provider.ai.HannahAiForeignOwnershipPredictionClient;
import com.hana.omnilens.provider.ai.HannahAiForeignOwnershipPredictionRequest;
import com.hana.omnilens.provider.ai.HannahAiForeignOwnershipPredictionResponse;
import com.hana.omnilens.provider.market.ForeignOwnershipSnapshot;

@Service
public class ForeignOwnershipPredictionPrecomputeService {

    private static final Logger log = LoggerFactory.getLogger(ForeignOwnershipPredictionPrecomputeService.class);
    private static final BigDecimal FOREIGN_LIMIT_WARNING_RATE = new BigDecimal("100.0000");
    private static final int HISTORY_LIMIT = 30;

    private final ForeignOwnershipSnapshotCache snapshotCache;
    private final ForeignOwnershipDailySnapshotRepository dailySnapshotRepository;
    private final ForeignOwnershipPredictionCache predictionCache;
    private final HannahAiForeignOwnershipPredictionClient hannahClient;
    private final ForeignOwnershipPredictionPrecomputeProperties properties;
    private final Clock clock;

    @Autowired
    public ForeignOwnershipPredictionPrecomputeService(
            ForeignOwnershipSnapshotCache snapshotCache,
            ForeignOwnershipDailySnapshotRepository dailySnapshotRepository,
            ForeignOwnershipPredictionCache predictionCache,
            HannahAiForeignOwnershipPredictionClient hannahClient,
            ForeignOwnershipPredictionPrecomputeProperties properties) {
        this(
                snapshotCache,
                dailySnapshotRepository,
                predictionCache,
                hannahClient,
                properties,
                Clock.systemUTC());
    }

    ForeignOwnershipPredictionPrecomputeService(
            ForeignOwnershipSnapshotCache snapshotCache,
            ForeignOwnershipDailySnapshotRepository dailySnapshotRepository,
            ForeignOwnershipPredictionCache predictionCache,
            HannahAiForeignOwnershipPredictionClient hannahClient,
            ForeignOwnershipPredictionPrecomputeProperties properties,
            Clock clock) {
        this.snapshotCache = snapshotCache;
        this.dailySnapshotRepository = dailySnapshotRepository;
        this.predictionCache = predictionCache;
        this.hannahClient = hannahClient;
        this.properties = properties;
        this.clock = clock;
    }

    public ForeignOwnershipPredictionPrecomputeResult precomputeRestrictedUniverse() {
        if (!properties.enabled()) {
            throw new IllegalStateException("Foreign ownership prediction precompute is disabled");
        }
        List<String> stockCodes = ForeignOwnershipRestrictedStockUniverse.stockCodes().stream()
                .limit(properties.stockLimit())
                .toList();
        List<ForeignOwnershipPredictionPrecomputeResult.StockResult> stockResults =
                new ArrayList<>();
        int precomputedCount = 0;
        int skippedCount = 0;
        int failedCount = 0;

        for (String stockCode : stockCodes) {
            Optional<ForeignOwnershipSnapshot> snapshot = snapshotCache.find(stockCode);
            if (snapshot.isEmpty()) {
                skippedCount++;
                stockResults.add(new ForeignOwnershipPredictionPrecomputeResult.StockResult(
                        stockCode,
                        "SKIPPED",
                        null,
                        "foreign ownership snapshot is missing"));
                continue;
            }
            try {
                ForeignOwnershipPrediction prediction = precomputeStock(snapshot.orElseThrow());
                predictionCache.put(stockCode, prediction);
                precomputedCount++;
                stockResults.add(new ForeignOwnershipPredictionPrecomputeResult.StockResult(
                        stockCode,
                        "PRECOMPUTED",
                        prediction.modelVersion(),
                        null));
            } catch (ProviderCircuitOpenException | RestClientException | IllegalStateException exception) {
                failedCount++;
                log.warn("Foreign ownership prediction precompute failed stockCode={}",
                        stockCode, exception);
                stockResults.add(new ForeignOwnershipPredictionPrecomputeResult.StockResult(
                        stockCode,
                        "FAILED",
                        null,
                        exception.getClass().getSimpleName()));
            }
        }

        return new ForeignOwnershipPredictionPrecomputeResult(
                stockCodes.size(),
                precomputedCount,
                skippedCount,
                failedCount,
                Instant.now(clock),
                stockResults);
    }

    public void precomputeAfterRefreshIfEnabled(ForeignOwnershipBackfillResult backfillResult) {
        if (!properties.enabled() || !properties.triggerAfterRefresh()) {
            return;
        }
        if (backfillResult.savedCount() <= 0) {
            return;
        }
        try {
            precomputeRestrictedUniverse();
        } catch (RuntimeException exception) {
            log.warn(
                    "Foreign ownership prediction precompute failed after refresh savedCount={} status={}",
                    backfillResult.savedCount(),
                    backfillResult.status(),
                    exception);
        }
    }

    private ForeignOwnershipPrediction precomputeStock(ForeignOwnershipSnapshot snapshot) {
        List<ForeignOwnershipDailySnapshot> history = dailySnapshotRepository.findRecent(
                snapshot.stockCode(),
                snapshot.baseDate(),
                HISTORY_LIMIT);
        if (isZeroForeignLimitRestricted(snapshot)) {
            return zeroForeignLimitPrediction(snapshot, history);
        }
        return toForeignOwnershipPrediction(hannahClient.predict(
                new HannahAiForeignOwnershipPredictionRequest(
                        snapshot.stockCode(),
                        "BUY",
                        0L,
                        snapshot.foreignOwnedQuantity(),
                        snapshot.foreignOwnershipRate(),
                        snapshot.foreignLimitQuantity(),
                        snapshot.foreignLimitExhaustionRate(),
                        snapshot.baseDate(),
                        0L,
                        history.stream()
                                .map(this::toHannahAiForeignOwnershipHistoryPoint)
                                .toList())));
    }

    private HannahAiForeignOwnershipHistoryPoint toHannahAiForeignOwnershipHistoryPoint(
            ForeignOwnershipDailySnapshot snapshot) {
        return new HannahAiForeignOwnershipHistoryPoint(
                snapshot.baseDate(),
                snapshot.foreignOwnedQuantity(),
                snapshot.foreignOwnershipRate(),
                snapshot.foreignLimitQuantity(),
                snapshot.foreignLimitExhaustionRate());
    }

    private ForeignOwnershipPrediction toForeignOwnershipPrediction(
            HannahAiForeignOwnershipPredictionResponse response) {
        return new ForeignOwnershipPrediction(
                response.minForeignLimitExhaustionRate(),
                response.baseForeignLimitExhaustionRate(),
                response.maxForeignLimitExhaustionRate(),
                response.orderImpactRate(),
                response.intradayUncertaintyRate(),
                response.observedIntradayVolume(),
                response.trendDailyChangeRate(),
                response.historyObservationCount(),
                response.historyWindowDays(),
                response.baseDate(),
                response.calculatedAt(),
                response.confidenceLevel(),
                response.confidenceScore(),
                response.modelVersion(),
                response.source());
    }

    private ForeignOwnershipPrediction zeroForeignLimitPrediction(
            ForeignOwnershipSnapshot snapshot,
            List<ForeignOwnershipDailySnapshot> history) {
        BigDecimal zeroRate = BigDecimal.ZERO.setScale(6);
        return new ForeignOwnershipPrediction(
                zeroRate,
                zeroRate,
                FOREIGN_LIMIT_WARNING_RATE.setScale(6, RoundingMode.HALF_UP),
                BigDecimal.ZERO.setScale(6),
                BigDecimal.ZERO.setScale(6),
                0L,
                BigDecimal.ZERO.setScale(6),
                history.size(),
                0,
                snapshot.baseDate(),
                Instant.now(clock),
                "FOREIGN_LIMIT_ZERO_NOT_ACQUIRABLE",
                BigDecimal.ONE.setScale(4),
                "foreign-ownership-zero-limit-v1",
                "KRX_FOREIGN_OWNERSHIP_ZERO_LIMIT");
    }

    private boolean isZeroForeignLimitRestricted(ForeignOwnershipSnapshot snapshot) {
        return ForeignOwnershipRestrictedStockUniverse.isZeroLimitRestrictedStockCode(snapshot.stockCode())
                && snapshot.foreignOwnedQuantity() == 0L
                && snapshot.foreignLimitQuantity() == 0L
                && snapshot.foreignOwnershipRate().signum() == 0
                && snapshot.foreignLimitExhaustionRate().signum() == 0;
    }
}

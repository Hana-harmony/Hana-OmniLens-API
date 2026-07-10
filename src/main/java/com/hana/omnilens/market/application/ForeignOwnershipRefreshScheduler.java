package com.hana.omnilens.market.application;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.hana.omnilens.config.ForeignOwnershipRefreshProperties;

@Component
public class ForeignOwnershipRefreshScheduler {

    private static final Logger log = LoggerFactory.getLogger(ForeignOwnershipRefreshScheduler.class);
    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    private final ForeignOwnershipRefreshService refreshService;
    private final ForeignOwnershipRefreshProperties properties;
    private final ForeignOwnershipModelTrainingService modelTrainingService;
    private final ForeignOwnershipPredictionPrecomputeService predictionPrecomputeService;
    private final Clock clock;

    @Autowired
    public ForeignOwnershipRefreshScheduler(
            ForeignOwnershipRefreshService refreshService,
            ForeignOwnershipRefreshProperties properties,
            ForeignOwnershipModelTrainingService modelTrainingService,
            ForeignOwnershipPredictionPrecomputeService predictionPrecomputeService) {
        this(
                refreshService,
                properties,
                modelTrainingService,
                predictionPrecomputeService,
                Clock.system(KOREA_ZONE));
    }

    ForeignOwnershipRefreshScheduler(
            ForeignOwnershipRefreshService refreshService,
            ForeignOwnershipRefreshProperties properties,
            Clock clock) {
        this(refreshService, properties, null, null, clock);
    }

    ForeignOwnershipRefreshScheduler(
            ForeignOwnershipRefreshService refreshService,
            ForeignOwnershipRefreshProperties properties,
            ForeignOwnershipModelTrainingService modelTrainingService,
            ForeignOwnershipPredictionPrecomputeService predictionPrecomputeService,
            Clock clock) {
        this.refreshService = refreshService;
        this.properties = properties;
        this.modelTrainingService = modelTrainingService;
        this.predictionPrecomputeService = predictionPrecomputeService;
        this.clock = clock;
    }

    @Scheduled(
            cron = "${omnilens.market.foreign-ownership-refresh.cron:0 10 8,16 * * MON-FRI}",
            zone = "Asia/Seoul")
    public void refreshConfiguredForeignOwnership() {
        if (!properties.enabled()) {
            return;
        }

        LocalDate baseDate = scheduledBaseDate();
        ForeignOwnershipCollectionResult collectionResult = refreshService.collect(
                baseDate,
                properties.stockCodes(),
                properties.stockLimit(),
                properties.requestDelayMs());
        log.info(
                "Scheduled current foreign ownership refresh completed baseDate={} requested={} refreshed={} failed={} status={}",
                collectionResult.baseDate(),
                collectionResult.requestedCount(),
                collectionResult.refreshedCount(),
                collectionResult.failedCount(),
                collectionResult.status());
        LocalDate fromDate = baseDate.minusDays(properties.backfillLookbackDays());
        ForeignOwnershipBackfillResult backfillResult = refreshService.backfillMissing(
                fromDate,
                baseDate,
                properties.stockCodes(),
                properties.stockLimit(),
                properties.requestDelayMs());
        log.info(
                "Scheduled foreign ownership backfill completed fromDate={} toDate={} requestedStocks={} missingDates={} saved={} failedDates={} status={}",
                backfillResult.fromDate(),
                backfillResult.toDate(),
                backfillResult.requestedStockCount(),
                backfillResult.missingDateCount(),
                backfillResult.savedCount(),
                backfillResult.failedDateCount(),
                backfillResult.status());
        if (modelTrainingService != null) {
            modelTrainingService.retrainAfterRefreshIfEnabled(backfillResult);
        }
        if (predictionPrecomputeService != null) {
            predictionPrecomputeService.precomputeAfterRefreshIfEnabled(collectionResult);
            predictionPrecomputeService.precomputeAfterRefreshIfEnabled(backfillResult);
        }
    }

    private LocalDate scheduledBaseDate() {
        LocalDate baseDate = ForeignOwnershipTradingDatePolicy.expectedBaseDate(clock);
        for (int offset = 1; offset < properties.baseDateOffsetDays(); offset++) {
            baseDate = ForeignOwnershipTradingDatePolicy.previousWeekday(baseDate);
        }
        return baseDate;
    }
}

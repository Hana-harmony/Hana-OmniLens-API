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
    private final Clock clock;

    @Autowired
    public ForeignOwnershipRefreshScheduler(
            ForeignOwnershipRefreshService refreshService,
            ForeignOwnershipRefreshProperties properties) {
        this(refreshService, properties, Clock.system(KOREA_ZONE));
    }

    ForeignOwnershipRefreshScheduler(
            ForeignOwnershipRefreshService refreshService,
            ForeignOwnershipRefreshProperties properties,
            Clock clock) {
        this.refreshService = refreshService;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(
            fixedDelayString = "${omnilens.market.foreign-ownership-refresh.fixed-delay-ms:86400000}",
            initialDelayString = "${omnilens.market.foreign-ownership-refresh.initial-delay-ms:60000}")
    public void refreshConfiguredForeignOwnership() {
        if (!properties.enabled()) {
            return;
        }

        LocalDate baseDate = LocalDate.now(clock).minusDays(properties.baseDateOffsetDays());
        ForeignOwnershipCollectionResult result = refreshService.collect(
                baseDate,
                properties.stockCodes(),
                properties.stockLimit(),
                properties.requestDelayMs());
        log.info(
                "Scheduled foreign ownership refresh completed baseDate={} requested={} refreshed={} failed={} status={}",
                result.baseDate(),
                result.requestedCount(),
                result.refreshedCount(),
                result.failedCount(),
                result.status());
    }
}

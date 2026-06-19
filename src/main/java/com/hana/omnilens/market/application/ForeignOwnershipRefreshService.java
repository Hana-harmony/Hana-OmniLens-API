package com.hana.omnilens.market.application;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import com.hana.omnilens.market.domain.StockSummary;
import com.hana.omnilens.provider.ProviderCircuitOpenException;
import com.hana.omnilens.provider.market.KrxForeignOwnershipClient;
import com.hana.omnilens.provider.market.KrxForeignOwnershipSnapshot;

@Service
public class ForeignOwnershipRefreshService {

    private static final Logger log = LoggerFactory.getLogger(ForeignOwnershipRefreshService.class);
    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");
    private static final String SOURCE = "KRX_FOREIGN_OWNERSHIP";

    private final KrxForeignOwnershipClient krxForeignOwnershipClient;
    private final StockMasterRepository stockMasterRepository;
    private final ForeignOwnershipSnapshotCache foreignOwnershipSnapshotCache;
    private final Clock clock;

    @Autowired
    public ForeignOwnershipRefreshService(
            KrxForeignOwnershipClient krxForeignOwnershipClient,
            StockMasterRepository stockMasterRepository,
            ForeignOwnershipSnapshotCache foreignOwnershipSnapshotCache) {
        this(
                krxForeignOwnershipClient,
                stockMasterRepository,
                foreignOwnershipSnapshotCache,
                Clock.system(KOREA_ZONE));
    }

    ForeignOwnershipRefreshService(
            KrxForeignOwnershipClient krxForeignOwnershipClient,
            StockMasterRepository stockMasterRepository,
            ForeignOwnershipSnapshotCache foreignOwnershipSnapshotCache,
            Clock clock) {
        this.krxForeignOwnershipClient = krxForeignOwnershipClient;
        this.stockMasterRepository = stockMasterRepository;
        this.foreignOwnershipSnapshotCache = foreignOwnershipSnapshotCache;
        this.clock = clock;
    }

    public ForeignOwnershipRefreshResult refresh(String stockCode, LocalDate baseDate) {
        StockSummary stock = stockMasterRepository.findByCode(stockCode)
                .orElseThrow(() -> new StockMasterNotFoundException(stockCode));
        LocalDate resolvedBaseDate = baseDate == null ? LocalDate.now(clock).minusDays(1) : baseDate;
        Optional<KrxForeignOwnershipSnapshot> snapshot = findSnapshot(stock, resolvedBaseDate);
        snapshot.ifPresent(foreignOwnershipSnapshotCache::put);
        return new ForeignOwnershipRefreshResult(stock.stockCode(), resolvedBaseDate, snapshot, SOURCE);
    }

    private Optional<KrxForeignOwnershipSnapshot> findSnapshot(StockSummary stock, LocalDate baseDate) {
        try {
            return krxForeignOwnershipClient.findForeignOwnership(
                    stock.stockCode(),
                    stock.stockName(),
                    stock.isinCode(),
                    baseDate);
        } catch (ProviderCircuitOpenException | RestClientException exception) {
            log.warn("KRX foreign ownership refresh failed stockCode={} baseDate={}", stock.stockCode(), baseDate, exception);
            return Optional.empty();
        }
    }
}

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
import com.hana.omnilens.provider.market.ForeignOwnershipSnapshot;
import com.hana.omnilens.provider.market.KisCurrentPriceClient;

@Service
public class ForeignOwnershipRefreshService {

    private static final Logger log = LoggerFactory.getLogger(ForeignOwnershipRefreshService.class);
    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");
    private static final String SOURCE = "KIS_CURRENT_PRICE_FOREIGN_OWNERSHIP";

    private final KisCurrentPriceClient kisCurrentPriceClient;
    private final StockMasterRepository stockMasterRepository;
    private final ForeignOwnershipSnapshotCache foreignOwnershipSnapshotCache;
    private final Clock clock;

    @Autowired
    public ForeignOwnershipRefreshService(
            KisCurrentPriceClient kisCurrentPriceClient,
            StockMasterRepository stockMasterRepository,
            ForeignOwnershipSnapshotCache foreignOwnershipSnapshotCache) {
        this(
                kisCurrentPriceClient,
                stockMasterRepository,
                foreignOwnershipSnapshotCache,
                Clock.system(KOREA_ZONE));
    }

    ForeignOwnershipRefreshService(
            KisCurrentPriceClient kisCurrentPriceClient,
            StockMasterRepository stockMasterRepository,
            ForeignOwnershipSnapshotCache foreignOwnershipSnapshotCache,
            Clock clock) {
        this.kisCurrentPriceClient = kisCurrentPriceClient;
        this.stockMasterRepository = stockMasterRepository;
        this.foreignOwnershipSnapshotCache = foreignOwnershipSnapshotCache;
        this.clock = clock;
    }

    public ForeignOwnershipRefreshResult refresh(String stockCode, LocalDate baseDate) {
        StockSummary stock = stockMasterRepository.findByCode(stockCode)
                .orElseThrow(() -> new StockMasterNotFoundException(stockCode));
        LocalDate resolvedBaseDate = baseDate == null ? LocalDate.now(clock).minusDays(1) : baseDate;
        Optional<ForeignOwnershipSnapshot> snapshot = findSnapshot(stock, resolvedBaseDate);
        snapshot.ifPresent(foreignOwnershipSnapshotCache::put);
        return new ForeignOwnershipRefreshResult(stock.stockCode(), resolvedBaseDate, snapshot, SOURCE);
    }

    private Optional<ForeignOwnershipSnapshot> findSnapshot(StockSummary stock, LocalDate baseDate) {
        try {
            return kisCurrentPriceClient.findCurrentPrice(stock.stockCode())
                    .flatMap(snapshot -> snapshot.foreignOwnershipSnapshot(baseDate));
        } catch (ProviderCircuitOpenException | RestClientException exception) {
            log.warn("KIS foreign ownership refresh failed stockCode={} baseDate={}", stock.stockCode(), baseDate, exception);
            return Optional.empty();
        }
    }
}

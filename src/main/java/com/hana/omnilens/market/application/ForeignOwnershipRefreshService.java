package com.hana.omnilens.market.application;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import com.hana.omnilens.market.domain.ForeignOwnershipDailySnapshot;
import com.hana.omnilens.market.domain.StockSummary;
import com.hana.omnilens.provider.ProviderCircuitOpenException;
import com.hana.omnilens.provider.market.ForeignOwnershipSnapshot;
import com.hana.omnilens.provider.market.KisCurrentPriceClient;

@Service
public class ForeignOwnershipRefreshService {

    private static final Logger log = LoggerFactory.getLogger(ForeignOwnershipRefreshService.class);
    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");
    private static final String SOURCE = "KIS_CURRENT_PRICE_FOREIGN_OWNERSHIP";
    private static final int DEFAULT_COLLECTION_LIMIT = 2_500;

    private final KisCurrentPriceClient kisCurrentPriceClient;
    private final StockMasterRepository stockMasterRepository;
    private final ForeignOwnershipSnapshotCache foreignOwnershipSnapshotCache;
    private final ForeignOwnershipDailySnapshotRepository foreignOwnershipDailySnapshotRepository;
    private final Clock clock;

    @Autowired
    public ForeignOwnershipRefreshService(
            KisCurrentPriceClient kisCurrentPriceClient,
            StockMasterRepository stockMasterRepository,
            ForeignOwnershipSnapshotCache foreignOwnershipSnapshotCache,
            ForeignOwnershipDailySnapshotRepository foreignOwnershipDailySnapshotRepository) {
        this(
                kisCurrentPriceClient,
                stockMasterRepository,
                foreignOwnershipSnapshotCache,
                foreignOwnershipDailySnapshotRepository,
                Clock.system(KOREA_ZONE));
    }

    ForeignOwnershipRefreshService(
            KisCurrentPriceClient kisCurrentPriceClient,
            StockMasterRepository stockMasterRepository,
            ForeignOwnershipSnapshotCache foreignOwnershipSnapshotCache,
            ForeignOwnershipDailySnapshotRepository foreignOwnershipDailySnapshotRepository,
            Clock clock) {
        this.kisCurrentPriceClient = kisCurrentPriceClient;
        this.stockMasterRepository = stockMasterRepository;
        this.foreignOwnershipSnapshotCache = foreignOwnershipSnapshotCache;
        this.foreignOwnershipDailySnapshotRepository = foreignOwnershipDailySnapshotRepository;
        this.clock = clock;
    }

    public ForeignOwnershipRefreshResult refresh(String stockCode, LocalDate baseDate) {
        StockSummary stock = stockMasterRepository.findByCode(stockCode)
                .orElseThrow(() -> new StockMasterNotFoundException(stockCode));
        LocalDate resolvedBaseDate = baseDate == null ? LocalDate.now(clock).minusDays(1) : baseDate;
        return refreshStock(stock, resolvedBaseDate);
    }

    public ForeignOwnershipCollectionResult collect(
            LocalDate baseDate,
            List<String> stockCodes,
            int limit) {
        return collect(baseDate, stockCodes, limit, 0L);
    }

    public ForeignOwnershipCollectionResult collect(
            LocalDate baseDate,
            List<String> stockCodes,
            int limit,
            long requestDelayMs) {
        LocalDate resolvedBaseDate = baseDate == null ? LocalDate.now(clock).minusDays(1) : baseDate;
        int resolvedLimit = limit <= 0 ? DEFAULT_COLLECTION_LIMIT : Math.min(limit, DEFAULT_COLLECTION_LIMIT);
        List<StockSummary> stocks = targetStocks(stockCodes, resolvedLimit);
        List<ForeignOwnershipCollectionResult.StockResult> stockResults = new ArrayList<>();
        int refreshedCount = 0;
        int failedCount = 0;
        long safeRequestDelayMs = Math.max(0L, requestDelayMs);

        if (stockCodes != null && !stockCodes.isEmpty()) {
            Set<String> knownStockCodes = stocks.stream()
                    .map(StockSummary::stockCode)
                    .collect(LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll);
            for (String stockCode : distinctStockCodes(stockCodes)) {
                if (!knownStockCodes.contains(stockCode)) {
                    failedCount++;
                    stockResults.add(new ForeignOwnershipCollectionResult.StockResult(
                            stockCode,
                            false,
                            "STOCK_NOT_FOUND",
                            "Stock master not found"));
                }
            }
        }

        for (int index = 0; index < stocks.size(); index++) {
            StockSummary stock = stocks.get(index);
            waitForProviderQuota(index, safeRequestDelayMs);
            try {
                ForeignOwnershipRefreshResult result = refreshStock(stock, resolvedBaseDate);
                if (result.refreshed()) {
                    refreshedCount++;
                    stockResults.add(new ForeignOwnershipCollectionResult.StockResult(
                            stock.stockCode(),
                            true,
                            "REFRESHED",
                            null));
                } else {
                    failedCount++;
                    stockResults.add(new ForeignOwnershipCollectionResult.StockResult(
                            stock.stockCode(),
                            false,
                            "PROVIDER_EMPTY",
                            "KIS current price did not include foreign ownership snapshot"));
                }
            } catch (RuntimeException exception) {
                failedCount++;
                log.warn("Foreign ownership collection failed stockCode={} baseDate={}",
                        stock.stockCode(), resolvedBaseDate, exception);
                stockResults.add(new ForeignOwnershipCollectionResult.StockResult(
                        stock.stockCode(),
                        false,
                        "FAILED",
                        exception.getClass().getSimpleName()));
            }
        }

        return new ForeignOwnershipCollectionResult(
                resolvedBaseDate,
                stockResults.size(),
                refreshedCount,
                failedCount,
                SOURCE,
                collectionStatus(stockResults.size(), refreshedCount, failedCount),
                List.copyOf(stockResults));
    }

    private void waitForProviderQuota(int index, long requestDelayMs) {
        if (index == 0 || requestDelayMs <= 0) {
            return;
        }
        try {
            // KIS 초당 호출 제한을 넘지 않도록 종목별 요청 간격을 둔다.
            Thread.sleep(requestDelayMs);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Foreign ownership collection interrupted", exception);
        }
    }

    private ForeignOwnershipRefreshResult refreshStock(StockSummary stock, LocalDate baseDate) {
        Optional<ForeignOwnershipSnapshot> snapshot = findSnapshot(stock, baseDate);
        snapshot.ifPresent(this::storeSnapshot);
        return new ForeignOwnershipRefreshResult(stock.stockCode(), baseDate, snapshot, SOURCE);
    }

    private List<StockSummary> targetStocks(List<String> stockCodes, int limit) {
        if (stockCodes == null || stockCodes.isEmpty()) {
            return stockMasterRepository.findAll(limit);
        }
        return distinctStockCodes(stockCodes).stream()
                .map(stockMasterRepository::findByCode)
                .flatMap(Optional::stream)
                .toList();
    }

    private List<String> distinctStockCodes(List<String> stockCodes) {
        LinkedHashSet<String> distinctStockCodes = stockCodes.stream()
                .filter(stockCode -> stockCode != null && stockCode.matches("\\d{6}"))
                .collect(LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll);
        return distinctStockCodes.stream().toList();
    }

    private String collectionStatus(int requestedCount, int refreshedCount, int failedCount) {
        if (requestedCount == 0) {
            return "EMPTY";
        }
        if (failedCount == 0) {
            return "SUCCESS";
        }
        if (refreshedCount > 0) {
            return "PARTIAL";
        }
        return "FAILED";
    }

    private void storeSnapshot(ForeignOwnershipSnapshot snapshot) {
        foreignOwnershipSnapshotCache.put(snapshot);
        foreignOwnershipDailySnapshotRepository.upsert(new ForeignOwnershipDailySnapshot(
                snapshot.stockCode(),
                snapshot.baseDate(),
                snapshot.foreignOwnedQuantity(),
                snapshot.foreignOwnershipRate(),
                snapshot.foreignLimitQuantity(),
                snapshot.foreignLimitExhaustionRate(),
                SOURCE,
                clock.instant()));
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

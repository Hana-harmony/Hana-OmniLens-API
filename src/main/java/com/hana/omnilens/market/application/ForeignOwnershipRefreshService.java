package com.hana.omnilens.market.application;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.hana.omnilens.market.domain.ForeignOwnershipDailySnapshot;
import com.hana.omnilens.market.domain.StockSummary;
import com.hana.omnilens.provider.market.ForeignOwnershipHistoricalSnapshotClient;
import com.hana.omnilens.provider.market.ForeignOwnershipSnapshot;

@Service
public class ForeignOwnershipRefreshService {

    private static final Logger log = LoggerFactory.getLogger(ForeignOwnershipRefreshService.class);
    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");
    private static final String SOURCE = "KRX_DATA_MARKETPLACE_FOREIGN_OWNERSHIP";
    private static final String HISTORICAL_SOURCE = SOURCE;
    private static final int DEFAULT_COLLECTION_LIMIT = 5_000;
    private static final int DEFAULT_BACKFILL_LOOKBACK_DAYS = 400;
    private static final int MAX_HISTORICAL_REQUEST_DAYS = 360;

    private final StockMasterRepository stockMasterRepository;
    private final ForeignOwnershipSnapshotCache foreignOwnershipSnapshotCache;
    private final ForeignOwnershipDailySnapshotRepository foreignOwnershipDailySnapshotRepository;
    private final MarketDailyPriceRepository marketDailyPriceRepository;
    private final ForeignOwnershipHistoricalSnapshotClient historicalSnapshotClient;
    private final Clock clock;

    @Autowired
    public ForeignOwnershipRefreshService(
            StockMasterRepository stockMasterRepository,
            ForeignOwnershipSnapshotCache foreignOwnershipSnapshotCache,
            ForeignOwnershipDailySnapshotRepository foreignOwnershipDailySnapshotRepository,
            MarketDailyPriceRepository marketDailyPriceRepository,
            ForeignOwnershipHistoricalSnapshotClient historicalSnapshotClient) {
        this(
                stockMasterRepository,
                foreignOwnershipSnapshotCache,
                foreignOwnershipDailySnapshotRepository,
                marketDailyPriceRepository,
                historicalSnapshotClient,
                Clock.system(KOREA_ZONE));
    }

    ForeignOwnershipRefreshService(
            StockMasterRepository stockMasterRepository,
            ForeignOwnershipSnapshotCache foreignOwnershipSnapshotCache,
            ForeignOwnershipDailySnapshotRepository foreignOwnershipDailySnapshotRepository,
            MarketDailyPriceRepository marketDailyPriceRepository,
            ForeignOwnershipHistoricalSnapshotClient historicalSnapshotClient,
            Clock clock) {
        this.stockMasterRepository = stockMasterRepository;
        this.foreignOwnershipSnapshotCache = foreignOwnershipSnapshotCache;
        this.foreignOwnershipDailySnapshotRepository = foreignOwnershipDailySnapshotRepository;
        this.marketDailyPriceRepository = marketDailyPriceRepository;
        this.historicalSnapshotClient = historicalSnapshotClient;
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
                            "KRX foreign ownership provider did not return a snapshot"));
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

    public ForeignOwnershipBackfillResult backfillMissing(
            LocalDate fromDate,
            LocalDate toDate,
            List<String> stockCodes,
            int limit,
            long requestDelayMs) {
        LocalDate resolvedToDate = toDate == null ? previousWeekday(LocalDate.now(clock)) : toDate;
        LocalDate resolvedFromDate = fromDate == null
                ? resolvedToDate.minusDays(DEFAULT_BACKFILL_LOOKBACK_DAYS)
                : fromDate;
        if (resolvedFromDate.isAfter(resolvedToDate)) {
            return new ForeignOwnershipBackfillResult(
                    resolvedFromDate,
                    resolvedToDate,
                    0,
                    0,
                    0,
                    0,
                    HISTORICAL_SOURCE,
                    "EMPTY",
                    List.of());
        }

        int resolvedLimit = limit <= 0 ? DEFAULT_COLLECTION_LIMIT : Math.min(limit, DEFAULT_COLLECTION_LIMIT);
        List<StockSummary> stocks = targetStocks(stockCodes, resolvedLimit);
        List<ForeignOwnershipBackfillResult.StockBackfillResult> stockResults = new ArrayList<>();
        int missingDateCount = 0;
        int savedCount = 0;
        int failedDateCount = 0;
        long safeRequestDelayMs = Math.max(0L, requestDelayMs);
        int providerRequestIndex = 0;

        for (int index = 0; index < stocks.size(); index++) {
            StockSummary stock = stocks.get(index);
            try {
                List<LocalDate> missingDates = missingTradingDates(stock.stockCode(), resolvedFromDate, resolvedToDate);
                if (missingDates.isEmpty()) {
                    stockResults.add(new ForeignOwnershipBackfillResult.StockBackfillResult(
                            stock.stockCode(),
                            0,
                            0,
                            "SKIPPED",
                            null));
                    continue;
                }

                missingDateCount += missingDates.size();
                Set<LocalDate> missingDateSet = new HashSet<>(missingDates);
                List<ForeignOwnershipSnapshot> snapshots = new ArrayList<>();
                for (DateRange chunk : historicalRequestChunks(missingDates)) {
                    waitForProviderQuota(providerRequestIndex, safeRequestDelayMs);
                    providerRequestIndex++;
                    snapshots.addAll(historicalSnapshotClient.findSnapshots(stock, chunk.fromDate(), chunk.toDate())
                            .stream()
                            .filter(snapshot -> stock.stockCode().equals(snapshot.stockCode()))
                            .filter(snapshot -> missingDateSet.contains(snapshot.baseDate()))
                            .toList());
                }
                int stockSavedCount = storeHistoricalSnapshots(snapshots);
                savedCount += stockSavedCount;
                int stockFailedCount = missingDates.size() - stockSavedCount;
                failedDateCount += stockFailedCount;
                stockResults.add(new ForeignOwnershipBackfillResult.StockBackfillResult(
                        stock.stockCode(),
                        missingDates.size(),
                        stockSavedCount,
                        stockBackfillStatus(missingDates.size(), stockSavedCount),
                        stockFailedCount == 0 ? null : "Historical provider did not return all missing dates"));
            } catch (RuntimeException exception) {
                List<LocalDate> missingDates = missingTradingDates(stock.stockCode(), resolvedFromDate, resolvedToDate);
                missingDateCount += missingDates.size();
                failedDateCount += missingDates.size();
                log.warn("Foreign ownership backfill failed stockCode={} fromDate={} toDate={}",
                        stock.stockCode(), resolvedFromDate, resolvedToDate, exception);
                stockResults.add(new ForeignOwnershipBackfillResult.StockBackfillResult(
                        stock.stockCode(),
                        missingDates.size(),
                        0,
                        "FAILED",
                        exception.getClass().getSimpleName()));
            }
        }

        return new ForeignOwnershipBackfillResult(
                resolvedFromDate,
                resolvedToDate,
                stocks.size(),
                missingDateCount,
                savedCount,
                failedDateCount,
                HISTORICAL_SOURCE,
                backfillStatus(stocks.size(), missingDateCount, savedCount, failedDateCount),
                List.copyOf(stockResults));
    }

    private void waitForProviderQuota(int index, long requestDelayMs) {
        if (index == 0 || requestDelayMs <= 0) {
            return;
        }
        try {
            // KRX 요청 제한을 넘지 않도록 종목별 요청 간격을 둔다.
            Thread.sleep(requestDelayMs);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Foreign ownership collection interrupted", exception);
        }
    }

    private List<DateRange> historicalRequestChunks(List<LocalDate> dates) {
        if (dates.isEmpty()) {
            return List.of();
        }
        List<DateRange> chunks = new ArrayList<>();
        LocalDate chunkStart = dates.get(0);
        LocalDate chunkEnd = chunkStart;
        for (LocalDate date : dates) {
            if (date.isAfter(chunkStart.plusDays(MAX_HISTORICAL_REQUEST_DAYS))) {
                chunks.add(new DateRange(chunkStart, chunkEnd));
                chunkStart = date;
            }
            chunkEnd = date;
        }
        chunks.add(new DateRange(chunkStart, chunkEnd));
        return chunks;
    }

    private record DateRange(LocalDate fromDate, LocalDate toDate) {
    }

    private ForeignOwnershipRefreshResult refreshStock(StockSummary stock, LocalDate baseDate) {
        Optional<ForeignOwnershipSnapshot> snapshot = findSnapshot(stock, baseDate);
        snapshot.ifPresent(this::storeSnapshot);
        return new ForeignOwnershipRefreshResult(stock.stockCode(), baseDate, snapshot, SOURCE);
    }

    private List<StockSummary> targetStocks(List<String> stockCodes, int limit) {
        if (stockCodes == null || stockCodes.isEmpty()) {
            return ForeignOwnershipRestrictedStockUniverse.stockCodes().stream()
                    .limit(limit)
                    .map(this::findRestrictedStock)
                    .flatMap(Optional::stream)
                    .toList();
        }
        return distinctStockCodes(stockCodes).stream()
                .map(stockMasterRepository::findByCode)
                .flatMap(Optional::stream)
                .toList();
    }

    private Optional<StockSummary> findRestrictedStock(String stockCode) {
        Optional<StockSummary> stock = stockMasterRepository.findByCode(stockCode);
        if (stock.isEmpty()) {
            // 법령 제한 종목이 master에서 사라지면 수집 누락을 운영 로그로 남긴다.
            log.warn("Foreign ownership restricted stock is missing from stock master stockCode={}", stockCode);
        }
        return stock;
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

    private int storeHistoricalSnapshots(List<ForeignOwnershipSnapshot> snapshots) {
        int savedCount = 0;
        for (ForeignOwnershipSnapshot snapshot : snapshots) {
            savedCount += foreignOwnershipDailySnapshotRepository.upsert(new ForeignOwnershipDailySnapshot(
                    snapshot.stockCode(),
                    snapshot.baseDate(),
                    snapshot.foreignOwnedQuantity(),
                    snapshot.foreignOwnershipRate(),
                    snapshot.foreignLimitQuantity(),
                    snapshot.foreignLimitExhaustionRate(),
                    HISTORICAL_SOURCE,
                    clock.instant()));
        }
        return savedCount;
    }

    private Optional<ForeignOwnershipSnapshot> findSnapshot(StockSummary stock, LocalDate baseDate) {
        try {
            return historicalSnapshotClient.findSnapshots(stock, baseDate, baseDate)
                    .stream()
                    .findFirst();
        } catch (RuntimeException exception) {
            log.warn("KRX foreign ownership refresh failed stockCode={} baseDate={}",
                    stock.stockCode(), baseDate, exception);
            return Optional.empty();
        }
    }

    private List<LocalDate> missingTradingDates(String stockCode, LocalDate fromDate, LocalDate toDate) {
        Set<LocalDate> existingDates = new HashSet<>(
                foreignOwnershipDailySnapshotRepository.findBaseDates(stockCode, fromDate, toDate));
        List<LocalDate> tradingDates = marketDailyPriceRepository.findTradingDates(fromDate, toDate);
        if (!tradingDates.isEmpty()) {
            return tradingDates.stream()
                    .filter(date -> !existingDates.contains(date))
                    .toList();
        }

        log.warn("KRX trading calendar is empty fromDate={} toDate={}, using weekday fallback", fromDate, toDate);
        List<LocalDate> missingDates = new ArrayList<>();
        for (LocalDate date = fromDate; !date.isAfter(toDate); date = date.plusDays(1)) {
            if (isWeekday(date) && !existingDates.contains(date)) {
                missingDates.add(date);
            }
        }
        return missingDates;
    }

    private static LocalDate previousWeekday(LocalDate today) {
        LocalDate date = today.minusDays(1);
        while (!isWeekday(date)) {
            date = date.minusDays(1);
        }
        return date;
    }

    private static boolean isWeekday(LocalDate date) {
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        return dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY;
    }

    private String stockBackfillStatus(int missingDateCount, int savedCount) {
        if (missingDateCount == 0) {
            return "SKIPPED";
        }
        if (savedCount == missingDateCount) {
            return "SUCCESS";
        }
        if (savedCount > 0) {
            return "PARTIAL";
        }
        return "PROVIDER_EMPTY";
    }

    private String backfillStatus(int requestedStockCount, int missingDateCount, int savedCount, int failedDateCount) {
        if (requestedStockCount == 0 || missingDateCount == 0) {
            return "EMPTY";
        }
        if (failedDateCount == 0) {
            return "SUCCESS";
        }
        if (savedCount > 0) {
            return "PARTIAL";
        }
        return "FAILED";
    }
}

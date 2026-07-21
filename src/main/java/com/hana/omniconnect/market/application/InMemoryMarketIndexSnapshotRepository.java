package com.hana.omniconnect.market.application;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.hana.omniconnect.market.domain.MarketIndexIntradayPrice;
import com.hana.omniconnect.market.domain.MarketIndexQuote;

public class InMemoryMarketIndexSnapshotRepository implements MarketIndexSnapshotRepository {

    private final Map<String, MarketIndexQuote> latest = new ConcurrentHashMap<>();
    private final Map<String, List<MarketIndexIntradayPrice>> intraday = new ConcurrentHashMap<>();

    @Override
    public void recordLatest(MarketIndexQuote indexQuote) {
        latest.put(indexQuote.indexCode(), indexQuote);
    }

    @Override
    public void recordRealtimeMinute(MarketIndexIntradayPrice price) {
        intraday.computeIfAbsent(price.indexCode(), ignored -> new ArrayList<>()).add(price);
    }

    @Override
    public int upsertIntradayPrices(List<MarketIndexIntradayPrice> prices) {
        if (prices == null || prices.isEmpty()) {
            return 0;
        }
        prices.forEach(this::recordRealtimeMinute);
        return prices.size();
    }

    @Override
    public List<MarketIndexQuote> findLatestIndices() {
        return latest.values().stream()
                .sorted(Comparator.comparing(MarketIndexQuote::indexCode))
                .toList();
    }

    @Override
    public List<MarketIndexIntradayPrice> findIntraday(String indexCode, LocalDate date, int limit) {
        return intraday.getOrDefault(indexCode, List.of()).stream()
                .filter(price -> price.bucketStart().toLocalDate().equals(date))
                .sorted(Comparator.comparing(MarketIndexIntradayPrice::bucketStart))
                .limit(limit)
                .toList();
    }

    @Override
    public Optional<MarketIndexIntradayPrice> findLatestBefore(String indexCode, LocalDate date) {
        return intraday.getOrDefault(indexCode, List.of()).stream()
                .filter(price -> price.bucketStart().toLocalDate().isBefore(date))
                .max(Comparator.comparing(MarketIndexIntradayPrice::bucketStart));
    }

    @Override
    public Optional<LocalDate> latestTradeDate(String indexCode) {
        return intraday.getOrDefault(indexCode, List.of()).stream()
                .map(price -> price.bucketStart().toLocalDate())
                .max(Comparator.naturalOrder());
    }
}

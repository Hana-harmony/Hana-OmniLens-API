package com.hana.omniconnect.market.application;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.hana.omniconnect.market.domain.MarketIndexIntradayPrice;
import com.hana.omniconnect.market.domain.MarketIndexQuote;

public interface MarketIndexSnapshotRepository {

    void recordLatest(MarketIndexQuote indexQuote);

    void recordRealtimeMinute(MarketIndexIntradayPrice price);

    int upsertIntradayPrices(List<MarketIndexIntradayPrice> prices);

    List<MarketIndexQuote> findLatestIndices();

    List<MarketIndexIntradayPrice> findIntraday(String indexCode, LocalDate date, int limit);

    Optional<LocalDate> latestTradeDate(String indexCode);
}

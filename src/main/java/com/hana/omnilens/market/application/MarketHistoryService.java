package com.hana.omnilens.market.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.hana.omnilens.market.domain.MarketDailyPrice;
import com.hana.omnilens.provider.market.KrxOpenApiDailyTrade;
import com.hana.omnilens.provider.market.KrxOpenApiDailyTradeClient;

@Service
public class MarketHistoryService {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");
    private static final String SOURCE = "KRX_OPEN_API_DAILY_TRADE";

    private final KrxOpenApiDailyTradeClient krxOpenApiDailyTradeClient;
    private final MarketDailyPriceRepository marketDailyPriceRepository;
    private final StockMasterRepository stockMasterRepository;
    private final Clock clock;

    @Autowired
    public MarketHistoryService(
            KrxOpenApiDailyTradeClient krxOpenApiDailyTradeClient,
            MarketDailyPriceRepository marketDailyPriceRepository,
            StockMasterRepository stockMasterRepository) {
        this(
                krxOpenApiDailyTradeClient,
                marketDailyPriceRepository,
                stockMasterRepository,
                Clock.system(KOREA_ZONE));
    }

    MarketHistoryService(
            KrxOpenApiDailyTradeClient krxOpenApiDailyTradeClient,
            MarketDailyPriceRepository marketDailyPriceRepository,
            StockMasterRepository stockMasterRepository,
            Clock clock) {
        this.krxOpenApiDailyTradeClient = krxOpenApiDailyTradeClient;
        this.marketDailyPriceRepository = marketDailyPriceRepository;
        this.stockMasterRepository = stockMasterRepository;
        this.clock = clock;
    }

    public List<MarketDailyPrice> getHistory(String stockCode, LocalDate from, LocalDate to, int limit) {
        stockMasterRepository.findByCode(stockCode)
                .orElseThrow(() -> new StockMasterNotFoundException(stockCode));
        LocalDate resolvedTo = to == null ? LocalDate.now(clock) : to;
        LocalDate resolvedFrom = from == null ? resolvedTo.minusYears(1) : from;
        if (resolvedFrom.isAfter(resolvedTo)) {
            return List.of();
        }
        return marketDailyPriceRepository.findByStockCode(stockCode, resolvedFrom, resolvedTo, limit);
    }

    public MarketHistoryCollectionResult collectDailyHistory(LocalDate baseDate) {
        LocalDate resolvedBaseDate = baseDate == null ? LocalDate.now(clock).minusDays(1) : baseDate;
        List<KrxOpenApiDailyTrade> trades = krxOpenApiDailyTradeClient.findAllDailyTrades(resolvedBaseDate);
        List<MarketDailyPrice> prices = trades.stream()
                .map(this::toDailyPrice)
                .filter(price -> stockMasterRepository.findByCode(price.stockCode()).isPresent())
                .toList();
        int savedCount = marketDailyPriceRepository.upsertAll(prices);
        return new MarketHistoryCollectionResult(resolvedBaseDate, trades.size(), savedCount, SOURCE);
    }

    private MarketDailyPrice toDailyPrice(KrxOpenApiDailyTrade trade) {
        return new MarketDailyPrice(
                trade.stockCode(),
                trade.baseDate(),
                trade.market(),
                trade.openingPriceKrw(),
                trade.highPriceKrw(),
                trade.lowPriceKrw(),
                trade.closingPriceKrw(),
                trade.changeRate(),
                trade.tradingVolume(),
                trade.tradingValueKrw(),
                trade.closingPriceKrw(),
                SOURCE,
                Instant.now(clock));
    }
}

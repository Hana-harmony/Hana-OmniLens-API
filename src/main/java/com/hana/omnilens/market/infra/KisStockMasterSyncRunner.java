package com.hana.omnilens.market.infra;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.hana.omnilens.config.StockMasterSeedProperties;
import com.hana.omnilens.market.domain.StockSummary;
import com.hana.omnilens.provider.market.KisStockMasterClient;
import com.hana.omnilens.provider.market.KisStockMasterMarket;

@Component
@Order(1)
public class KisStockMasterSyncRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(KisStockMasterSyncRunner.class);

    private final JdbcStockMasterRepository repository;
    private final StockMasterSeedProperties properties;
    private final KisStockMasterClient client;

    public KisStockMasterSyncRunner(
            JdbcStockMasterRepository repository,
            StockMasterSeedProperties properties,
            KisStockMasterClient client) {
        this.repository = repository;
        this.properties = properties;
        this.client = client;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.kisMasterSyncEnabled()) {
            return;
        }

        int syncedCount = 0;
        syncedCount += collect(KisStockMasterMarket.KOSPI, properties.kospiUrl());
        syncedCount += collect(KisStockMasterMarket.KOSDAQ, properties.kosdaqUrl());
        syncedCount += collect(KisStockMasterMarket.KONEX, properties.konexUrl());
        if (syncedCount == 0) {
            log.warn("KIS stock master sync skipped because no rows were downloaded");
            return;
        }
        log.info("KIS stock master synced: count={}", syncedCount);
    }

    private int collect(KisStockMasterMarket market, String url) {
        try {
            List<StockSummary> marketStocks = client.fetch(market, url);
            repository.reconcileMarketSnapshot(market.marketName(), marketStocks);
            log.info("KIS stock master downloaded: market={} count={}", market.marketName(), marketStocks.size());
            return marketStocks.size();
        } catch (Exception exception) {
            // 마스터 동기화 실패가 시세 API 기동 자체를 막지 않도록 기존 DB를 유지한다.
            log.warn("KIS stock master sync failed: market={} url={}", market.marketName(), url, exception);
            return 0;
        }
    }
}

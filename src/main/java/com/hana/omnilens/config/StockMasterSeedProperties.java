package com.hana.omnilens.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "omnilens.market.stock-master.seed")
public record StockMasterSeedProperties(
        boolean enabled,
        String location,
        boolean kisMasterSyncEnabled,
        String kospiUrl,
        String kosdaqUrl,
        String konexUrl
) {

    public StockMasterSeedProperties {
        location = location == null || location.isBlank()
                ? "classpath:data/stock-master-seed.csv"
                : location;
        kospiUrl = kospiUrl == null || kospiUrl.isBlank()
                ? "https://new.real.download.dws.co.kr/common/master/kospi_code.mst.zip"
                : kospiUrl;
        kosdaqUrl = kosdaqUrl == null || kosdaqUrl.isBlank()
                ? "https://new.real.download.dws.co.kr/common/master/kosdaq_code.mst.zip"
                : kosdaqUrl;
        konexUrl = konexUrl == null || konexUrl.isBlank()
                ? "https://new.real.download.dws.co.kr/common/master/konex_code.mst.zip"
                : konexUrl;
    }
}

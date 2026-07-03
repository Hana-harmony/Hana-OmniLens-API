package com.hana.omnilens.alert.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.hana.omnilens.market.application.StockMasterRepository;
import com.hana.omnilens.market.domain.StockSummary;

class AlertCollectionTargetUniverseProviderTest {

    @Test
    void combinesPriorityStocksAndSupportedForeignOwnershipRestrictedStocks() {
        StockMasterRepository repository = new StubStockMasterRepository(List.of(
                stock("005930", "삼성전자"),
                stock("000660", "SK하이닉스"),
                stock("015760", "한국전력"),
                stock("030200", "KT")));
        AlertCollectionTargetUniverseProvider provider = new AlertCollectionTargetUniverseProvider(repository);

        List<String> stockCodes = provider.defaultStockCodes(2, true);

        assertThat(stockCodes).containsExactly("005930", "000660", "015760", "030200");
    }

    @Test
    void canCollectOnlyPriorityStocksWhenForeignOwnershipRestrictedStocksAreDisabled() {
        StockMasterRepository repository = new StubStockMasterRepository(List.of(
                stock("005930", "삼성전자"),
                stock("000660", "SK하이닉스"),
                stock("015760", "한국전력")));
        AlertCollectionTargetUniverseProvider provider = new AlertCollectionTargetUniverseProvider(repository);

        List<String> stockCodes = provider.defaultStockCodes(2, false);

        assertThat(stockCodes).containsExactly("005930", "000660");
    }

    private static StockSummary stock(String stockCode, String stockName) {
        return new StockSummary(stockCode, stockName, stockName, "KOSPI", "KR" + stockCode + "0000", "");
    }

    private static class StubStockMasterRepository implements StockMasterRepository {

        private final List<StockSummary> stocks;
        private final Map<String, StockSummary> stocksByCode;

        private StubStockMasterRepository(List<StockSummary> stocks) {
            this.stocks = stocks;
            this.stocksByCode = stocks.stream()
                    .collect(java.util.stream.Collectors.toMap(StockSummary::stockCode, stock -> stock));
        }

        @Override
        public Optional<StockSummary> findByCode(String stockCode) {
            return Optional.ofNullable(stocksByCode.get(stockCode));
        }

        @Override
        public List<StockSummary> findAll(int limit) {
            return stocks.stream().limit(limit).toList();
        }

        @Override
        public List<StockSummary> search(String query) {
            return List.of();
        }
    }
}

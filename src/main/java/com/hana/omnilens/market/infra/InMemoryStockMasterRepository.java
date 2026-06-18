package com.hana.omnilens.market.infra;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.hana.omnilens.market.application.StockMasterRepository;
import com.hana.omnilens.market.domain.StockSummary;

public class InMemoryStockMasterRepository implements StockMasterRepository {

    private static final List<StockSummary> STOCKS = List.of(
            new StockSummary("005930", "삼성전자", "Samsung Electronics", "KOSPI", "KR7005930003", "00126380"),
            new StockSummary("000660", "SK하이닉스", "SK hynix", "KOSPI", "KR7000660001", "00164779"),
            new StockSummary("035420", "NAVER", "NAVER", "KOSPI", "KR7035420009", "00266961"),
            new StockSummary("005380", "현대차", "Hyundai Motor", "KOSPI", "KR7005380001", "00164742"),
            new StockSummary("035720", "카카오", "Kakao", "KOSPI", "KR7035720002", "00258801"),
            new StockSummary("207940", "삼성바이오로직스", "Samsung Biologics", "KOSPI", "KR7207940008", "00877059"));

    @Override
    public Optional<StockSummary> findByCode(String stockCode) {
        return STOCKS.stream()
                .filter(stock -> stock.stockCode().equals(stockCode))
                .findFirst();
    }

    @Override
    public List<StockSummary> findAll(int limit) {
        return STOCKS.stream()
                .sorted(Comparator.comparing(StockSummary::stockCode))
                .limit(limit)
                .toList();
    }

    @Override
    public List<StockSummary> search(String query) {
        String normalizedQuery = query.toLowerCase(Locale.ROOT);
        return STOCKS.stream()
                .filter(stock -> matches(stock, normalizedQuery))
                .sorted(Comparator.comparing(StockSummary::stockCode))
                .toList();
    }

    private boolean matches(StockSummary stock, String normalizedQuery) {
        return stock.stockCode().contains(normalizedQuery)
                || stock.stockName().toLowerCase(Locale.ROOT).contains(normalizedQuery)
                || stock.stockNameEn().toLowerCase(Locale.ROOT).contains(normalizedQuery);
    }
}

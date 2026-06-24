package com.hana.omnilens.provider.market;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.hana.omnilens.market.domain.StockSummary;

@Component
public class KisStockMasterFileParser {

    private static final Pattern STOCK_CODE = Pattern.compile("\\d{6}");
    private static final Pattern ISIN_CODE = Pattern.compile("[A-Z]{2}[A-Z0-9]{10}");

    public List<StockSummary> parse(KisStockMasterMarket market, String content) {
        List<StockSummary> stocks = new ArrayList<>();
        String[] lines = content.split("\\R");
        for (String line : lines) {
            if (line.isBlank()) {
                continue;
            }
            parseLine(market, line).ifPresent(stocks::add);
        }
        return List.copyOf(stocks);
    }

    private java.util.Optional<StockSummary> parseLine(KisStockMasterMarket market, String line) {
        if (line.length() <= market.tailWidth() + 21) {
            return java.util.Optional.empty();
        }

        String head = line.substring(0, line.length() - market.tailWidth());
        String tail = line.substring(line.length() - market.tailWidth());
        String stockCode = head.substring(0, 9).trim();
        String isinCode = head.substring(9, 21).trim();
        String stockName = head.substring(21).trim();

        if (!STOCK_CODE.matcher(stockCode).matches()
                || !ISIN_CODE.matcher(isinCode).matches()
                || stockName.isBlank()
                || isEtp(market, tail)) {
            return java.util.Optional.empty();
        }

        // KIS 주식 마스터에는 영문명이 없어 기존 수동 보강값을 업서트에서 보존한다.
        return java.util.Optional.of(new StockSummary(
                stockCode,
                stockName,
                stockName,
                market.marketName(),
                isinCode,
                ""));
    }

    private boolean isEtp(KisStockMasterMarket market, String tail) {
        int offset = market.etpFlagOffset();
        return offset >= 0 && tail.length() > offset && tail.charAt(offset) == 'Y';
    }
}

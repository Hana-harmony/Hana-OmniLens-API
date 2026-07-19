package com.hana.omniconnect.market.infra;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.hana.omniconnect.config.StockMasterSeedProperties;
import com.hana.omniconnect.market.domain.StockSummary;

@Component
@Order(0)
public class StockMasterSeedLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StockMasterSeedLoader.class);
    private static final Pattern STOCK_CODE = Pattern.compile("\\d{6}");
    private static final Pattern ISIN_CODE = Pattern.compile("[A-Z]{2}[A-Z0-9]{10}");
    private static final String HEADER = "stock_code,stock_name,stock_name_en,market,isin_code,dart_corp_code";

    private final JdbcStockMasterRepository repository;
    private final StockMasterSeedProperties properties;
    private final ResourceLoader resourceLoader;

    public StockMasterSeedLoader(
            JdbcStockMasterRepository repository,
            StockMasterSeedProperties properties,
            ResourceLoader resourceLoader) {
        this.repository = repository;
        this.properties = properties;
        this.resourceLoader = resourceLoader;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!properties.enabled()) {
            return;
        }
        if (repository.count() > 0) {
            return;
        }

        List<StockSummary> stocks = loadSeed();
        if (stocks.isEmpty()) {
            log.warn("stock master seed file is empty: {}", properties.location());
            return;
        }

        repository.insertAll(stocks);
        log.info("stock master seed loaded: count={}", stocks.size());
    }

    private List<StockSummary> loadSeed() throws Exception {
        Resource resource = resourceLoader.getResource(properties.location());
        if (!resource.exists()) {
            throw new IllegalStateException("Stock master seed file not found: " + properties.location());
        }

        List<StockSummary> stocks = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                resource.getInputStream(),
                StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            if (!HEADER.equals(header)) {
                throw new IllegalStateException("Invalid stock master seed header: " + properties.location());
            }

            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) {
                    continue;
                }
                stocks.add(parseLine(line, lineNumber));
            }
        }
        return List.copyOf(stocks);
    }

    private StockSummary parseLine(String line, int lineNumber) {
        String[] columns = line.split(",", -1);
        if (columns.length != 6) {
            throw new IllegalStateException("Invalid stock master seed row: line=" + lineNumber);
        }

        String stockCode = columns[0].trim();
        String stockName = columns[1].trim();
        String stockNameEn = columns[2].trim();
        String market = columns[3].trim();
        String isinCode = columns[4].trim();
        String dartCorpCode = columns[5].trim();

        // 운영 seed 오염을 초기에 막기 위해 핵심 식별자는 시작 시 검증한다.
        if (!STOCK_CODE.matcher(stockCode).matches()
                || stockName.isBlank()
                || stockNameEn.isBlank()
                || market.isBlank()
                || !ISIN_CODE.matcher(isinCode).matches()
                || (!dartCorpCode.isBlank() && dartCorpCode.length() != 8)) {
            throw new IllegalStateException("Invalid stock master seed value: line=" + lineNumber);
        }

        return new StockSummary(stockCode, stockName, stockNameEn, market, isinCode, dartCorpCode);
    }
}

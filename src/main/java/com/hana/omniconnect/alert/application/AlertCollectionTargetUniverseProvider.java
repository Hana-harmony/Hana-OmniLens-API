package com.hana.omniconnect.alert.application;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.hana.omniconnect.market.application.ForeignOwnershipRestrictedStockUniverse;
import com.hana.omniconnect.market.application.StockMasterRepository;
import com.hana.omniconnect.market.domain.StockSummary;

@Component
public class AlertCollectionTargetUniverseProvider {

    private final StockMasterRepository stockMasterRepository;

    public AlertCollectionTargetUniverseProvider(StockMasterRepository stockMasterRepository) {
        this.stockMasterRepository = stockMasterRepository;
    }

    public List<String> defaultStockCodes(
            int priorityStockLimit,
            boolean includeForeignOwnershipRestrictedStocks) {
        Set<String> stockCodes = new LinkedHashSet<>();
        stockMasterRepository.findAll(priorityStockLimit)
                .stream()
                .map(StockSummary::stockCode)
                .forEach(stockCodes::add);
        if (includeForeignOwnershipRestrictedStocks) {
            ForeignOwnershipRestrictedStockUniverse.stockCodes()
                    .stream()
                    .filter(stockCode -> stockMasterRepository.findByCode(stockCode).isPresent())
                    .forEach(stockCodes::add);
        }
        return List.copyOf(stockCodes);
    }
}

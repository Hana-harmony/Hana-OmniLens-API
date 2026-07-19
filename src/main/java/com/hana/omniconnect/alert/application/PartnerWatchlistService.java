package com.hana.omniconnect.alert.application;

import java.util.LinkedHashSet;
import java.util.List;

import org.springframework.stereotype.Service;

import com.hana.omniconnect.alert.api.PartnerWatchlistResponse;
import com.hana.omniconnect.market.application.StockMasterNotFoundException;
import com.hana.omniconnect.market.application.StockMasterRepository;

@Service
public class PartnerWatchlistService {

    private final PartnerWatchlistRepository partnerWatchlistRepository;
    private final StockMasterRepository stockMasterRepository;

    public PartnerWatchlistService(
            PartnerWatchlistRepository partnerWatchlistRepository,
            StockMasterRepository stockMasterRepository) {
        this.partnerWatchlistRepository = partnerWatchlistRepository;
        this.stockMasterRepository = stockMasterRepository;
    }

    public PartnerWatchlistResponse get(String partnerId) {
        return new PartnerWatchlistResponse(partnerId, partnerWatchlistRepository.findStockCodes(partnerId));
    }

    public PartnerWatchlistResponse replace(String partnerId, List<String> stockCodes) {
        List<String> normalizedStockCodes = stockCodes.stream()
                .distinct()
                .toList();
        validateSupportedStocks(normalizedStockCodes);
        return new PartnerWatchlistResponse(partnerId, partnerWatchlistRepository.replace(partnerId, normalizedStockCodes));
    }

    private void validateSupportedStocks(List<String> stockCodes) {
        for (String stockCode : new LinkedHashSet<>(stockCodes)) {
            if (stockMasterRepository.findByCode(stockCode).isEmpty()) {
                throw new StockMasterNotFoundException(stockCode);
            }
        }
    }
}

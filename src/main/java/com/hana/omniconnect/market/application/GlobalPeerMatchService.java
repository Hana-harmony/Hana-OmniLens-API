package com.hana.omniconnect.market.application;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import com.hana.omniconnect.market.domain.GlobalPeerMatch;
import com.hana.omniconnect.market.domain.GlobalPeerMatchResponse;
import com.hana.omniconnect.market.domain.GlobalPeerComparison;
import com.hana.omniconnect.market.domain.GlobalPeerKeyStrength;
import com.hana.omniconnect.market.domain.StockSummary;
import com.hana.omniconnect.provider.ProviderCircuitOpenException;
import com.hana.omniconnect.provider.ai.HannahAiGlobalPeerMatch;
import com.hana.omniconnect.provider.ai.HannahAiGlobalPeerMatchClient;
import com.hana.omniconnect.provider.ai.HannahAiGlobalPeerMatchRequest;
import com.hana.omniconnect.provider.ai.HannahAiGlobalPeerMatchResponse;

@Service
public class GlobalPeerMatchService {

    private static final Logger log = LoggerFactory.getLogger(GlobalPeerMatchService.class);

    private final StockMasterRepository stockMasterRepository;
    private final HannahAiGlobalPeerMatchClient hannahClient;

    public GlobalPeerMatchService(
            StockMasterRepository stockMasterRepository,
            HannahAiGlobalPeerMatchClient hannahClient) {
        this.stockMasterRepository = stockMasterRepository;
        this.hannahClient = hannahClient;
    }

    public GlobalPeerMatchResponse match(String stockCode) {
        StockSummary stock = stockMasterRepository.findByCode(stockCode)
                .orElseThrow(() -> new MarketDataUnavailableException(
                        "Stock master row not found: " + stockCode));
        try {
            return toResponse(hannahClient.match(new HannahAiGlobalPeerMatchRequest(
                    stock.stockCode(),
                    stock.stockName(),
                    stock.stockNameEn(),
                    stock.market(),
                    List.of(),
                    "",
                    5)));
        } catch (ProviderCircuitOpenException | RestClientException | IllegalStateException exception) {
            log.warn(
                    "Hannah AI global peer match failed stockCode={}: {}",
                    stockCode,
                    exception.toString());
            throw new MarketDataUnavailableException(
                    "Hannah AI global peer result is unavailable: " + stockCode);
        }
    }

    private GlobalPeerMatchResponse toResponse(HannahAiGlobalPeerMatchResponse response) {
        return new GlobalPeerMatchResponse(
                response.stockCode(),
                response.stockName(),
                response.stockNameEn(),
                response.headline(),
                response.summary(),
                toPeer(response.primaryPeer()),
                response.peers().stream().map(this::toPeer).toList(),
                response.comparisons().stream().map(this::toComparison).toList(),
                response.keyStrengths().stream().map(this::toKeyStrength).toList(),
                response.confidenceScore(),
                response.confidenceLevel(),
                response.modelVersion(),
                response.source());
    }

    private GlobalPeerMatch toPeer(HannahAiGlobalPeerMatch peer) {
        return new GlobalPeerMatch(
                peer.rank(),
                peer.ticker(),
                peer.companyName(),
                peer.exchange(),
                peer.country(),
                peer.similarityScore(),
                peer.businessTags(),
                peer.sector(),
                peer.industry(),
                peer.businessModel(),
                peer.scaleBucket(),
                peer.fiscalYear(),
                peer.marketCapUsd(),
                peer.revenueUsd(),
                peer.operatingIncomeUsd(),
                peer.netIncomeUsd(),
                peer.financialDataSource(),
                peer.financialSimilarityScore(),
                peer.matchedFactors(),
                peer.rationale());
    }

    private GlobalPeerComparison toComparison(
            com.hana.omniconnect.provider.ai.HannahAiGlobalPeerComparison comparison) {
        return new GlobalPeerComparison(
                comparison.dimension(),
                comparison.description(),
                toPeer(comparison.peer()));
    }

    private GlobalPeerKeyStrength toKeyStrength(
            com.hana.omniconnect.provider.ai.HannahAiGlobalPeerKeyStrength strength) {
        return new GlobalPeerKeyStrength(
                strength.title(),
                strength.description(),
                strength.iconKey());
    }

}

package com.hana.omnilens.market.application;

import java.math.BigDecimal;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import com.hana.omnilens.market.domain.GlobalPeerMatch;
import com.hana.omnilens.market.domain.GlobalPeerMatchResponse;
import com.hana.omnilens.market.domain.StockSummary;
import com.hana.omnilens.provider.ProviderCircuitOpenException;
import com.hana.omnilens.provider.ai.HannahAiGlobalPeerMatch;
import com.hana.omnilens.provider.ai.HannahAiGlobalPeerMatchClient;
import com.hana.omnilens.provider.ai.HannahAiGlobalPeerMatchRequest;
import com.hana.omnilens.provider.ai.HannahAiGlobalPeerMatchResponse;

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
                    "Hannah AI global peer match failed stockCode={}, falling back: {}",
                    stockCode,
                    exception.toString());
            return fallback(stock);
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

    private GlobalPeerMatchResponse fallback(StockSummary stock) {
        if ("196170".equals(stock.stockCode())) {
            GlobalPeerMatch peer = new GlobalPeerMatch(
                    1,
                    "HALO",
                    "Halozyme Therapeutics",
                    "NASDAQ_GLOBAL_SELECT",
                    "US",
                    new BigDecimal("0.4911"),
                    List.of("biotech platform", "drug delivery", "royalty licensing"),
                    "Health Care",
                    "Biotechnology",
                    "Biotech platform licensing",
                    "MID_CAP",
                    2025,
                    null,
                    new BigDecimal("1396611000"),
                    new BigDecimal("469006000"),
                    new BigDecimal("316889000"),
                    "SEC_COMPANYFACTS",
                    new BigDecimal("0.9996"),
                    List.of(
                            "Sector: both are Health Care companies.",
                            "Industry: both operate in Biotechnology.",
                            "Business model: both monetize platform drug-delivery technology through licensing.",
                            "Scale: both are treated as mid-cap biotech platform peers."),
                    "Both companies are biotech platform providers centered on drug-delivery technology, "
                            + "subcutaneous formulation conversion, and royalty-style licensing.");
            return new GlobalPeerMatchResponse(
                    stock.stockCode(),
                    stock.stockName(),
                    stock.stockNameEn().isBlank() ? "Alteogen" : stock.stockNameEn(),
                    "Alteogen Is The 'Halozyme Therapeutics' of South Korea — "
                            + "A Global Biotech Platform Leader",
                    "Alteogen is a high-margin Biotech Platform provider. Instead of developing "
                            + "its own new drugs, it licenses out its proprietary drug-delivery technology "
                            + "to global Big Pharma, securing long-term milestone and royalty fees.",
                    peer,
                    List.of(peer),
                    new BigDecimal("0.4911"),
                    "MEDIUM",
                    "global-peer-fallback-v1",
                    "OMNILENS_GLOBAL_PEER_FALLBACK");
        }
        GlobalPeerMatch peer = new GlobalPeerMatch(
                1,
                "MSFT",
                "Microsoft",
                "NASDAQ_GLOBAL_SELECT",
                "US",
                BigDecimal.ZERO,
                List.of("general listed company"),
                "Unclassified",
                "Unclassified",
                "Operating company",
                "UNKNOWN",
                null,
                null,
                null,
                null,
                null,
                "",
                null,
                List.of("Hannah AI peer model is unavailable, so no explainable peer factors were produced."),
                "Hannah AI peer model is unavailable, so OmniLens returned a low-confidence fallback.");
        return new GlobalPeerMatchResponse(
                stock.stockCode(),
                stock.stockName(),
                stock.stockNameEn(),
                (stock.stockNameEn().isBlank() ? stock.stockName() : stock.stockNameEn())
                        + " Global Peer Match Is Temporarily Unavailable",
                "The global peer model is temporarily unavailable. Please retry after the AI service recovers.",
                peer,
                List.of(peer),
                BigDecimal.ZERO,
                "LOW",
                "global-peer-fallback-v1",
                "OMNILENS_GLOBAL_PEER_FALLBACK");
    }
}

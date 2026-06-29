package com.hana.omnilens.provider.market;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import com.hana.omnilens.config.ExternalProviderProperties;
import com.hana.omnilens.market.application.RealtimeMarketDataIngestionService;
import com.hana.omnilens.market.application.StockMasterNotFoundException;
import com.hana.omnilens.market.application.StockMasterRepository;
import com.hana.omnilens.provider.ExternalProviderResiliencePolicy;

@Service
public class OnDemandKisRealtimeSubscriptionService {

    private static final String REGULAR = "REGULAR";
    private static final String AFTER_HOURS_REAL = "AFTER_HOURS_REAL";

    private final KisRealtimeWebSocketConnection regularConnection;
    private final KisRealtimeApprovalKeyProvider regularApprovalKeyProvider;
    private final KisRealtimeApprovalKeyProvider realApprovalKeyProvider;
    private final KisRealtimeSubscriptionFrameFactory frameFactory;
    private final ExternalProviderProperties.Kis realKisProperties;
    private final KisRealtimeWebSocketConnection realAfterHoursConnection;
    private final RealtimeMarketDataIngestionService ingestionService;
    private final StockMasterRepository stockMasterRepository;
    private final AtomicReference<String> activeAfterHoursStockCode = new AtomicReference<>("");
    private final AtomicBoolean realAfterHoursConnected = new AtomicBoolean(false);

    public OnDemandKisRealtimeSubscriptionService(
            KisRealtimeWebSocketConnection regularConnection,
            KisRealtimeApprovalKeyProvider regularApprovalKeyProvider,
            KisRealtimeSubscriptionFrameFactory frameFactory,
            ExternalProviderProperties properties,
            ExternalProviderResiliencePolicy resiliencePolicy,
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            RealtimeMarketDataIngestionService ingestionService,
            StockMasterRepository stockMasterRepository) {
        this.regularConnection = regularConnection;
        this.regularApprovalKeyProvider = regularApprovalKeyProvider;
        this.frameFactory = frameFactory;
        this.realKisProperties = properties.realKis();
        this.realApprovalKeyProvider = new KisRealtimeApprovalKeyProvider(
                restClientBuilder,
                this.realKisProperties,
                resiliencePolicy);
        this.realAfterHoursConnection = new StandardKisRealtimeWebSocketConnection(objectMapper);
        this.ingestionService = ingestionService;
        this.stockMasterRepository = stockMasterRepository;
    }

    public KisRealtimeSubscriptionRequestResult subscribeRegular(String stockCode) {
        ensureStockExists(stockCode);
        String approvalKey = regularApprovalKeyProvider.approvalKey();
        regularConnection.subscribe(List.of(frame(
                approvalKey,
                KisRealtimeTransaction.TRADE,
                KisRealtimeSubscriptionType.SUBSCRIBE,
                stockCode)));
        return new KisRealtimeSubscriptionRequestResult(
                stockCode,
                REGULAR,
                "SUBSCRIBED",
                "KIS regular realtime source subscription requested");
    }

    public KisRealtimeSubscriptionRequestResult unsubscribeRegular(String stockCode) {
        ensureStockExists(stockCode);
        String approvalKey = regularApprovalKeyProvider.approvalKey();
        regularConnection.unsubscribe(List.of(frame(
                approvalKey,
                KisRealtimeTransaction.TRADE,
                KisRealtimeSubscriptionType.UNSUBSCRIBE,
                stockCode)));
        return new KisRealtimeSubscriptionRequestResult(
                stockCode,
                REGULAR,
                "UNSUBSCRIBED",
                "KIS regular realtime source unsubscribe requested");
    }

    public KisRealtimeSubscriptionRequestResult subscribeRealAfterHours(String stockCode) {
        ensureStockExists(stockCode);
        if (!hasRealKisCredential()) {
            return new KisRealtimeSubscriptionRequestResult(
                    stockCode,
                    AFTER_HOURS_REAL,
                    "DISABLED",
                    "Real KIS credential is not configured");
        }
        String approvalKey = realApprovalKeyProvider.approvalKey();
        startRealAfterHoursConnectionIfNeeded();
        String previousStockCode = activeAfterHoursStockCode.getAndSet(stockCode);
        if (StringUtils.hasText(previousStockCode) && !previousStockCode.equals(stockCode)) {
            realAfterHoursConnection.unsubscribe(List.of(frame(
                    approvalKey,
                    KisRealtimeTransaction.AFTER_HOURS_TRADE,
                    KisRealtimeSubscriptionType.UNSUBSCRIBE,
                    previousStockCode)));
        }
        realAfterHoursConnection.subscribe(List.of(frame(
                approvalKey,
                KisRealtimeTransaction.AFTER_HOURS_TRADE,
                KisRealtimeSubscriptionType.SUBSCRIBE,
                stockCode)));
        return new KisRealtimeSubscriptionRequestResult(
                stockCode,
                AFTER_HOURS_REAL,
                "SUBSCRIBED",
                "Real KIS after-hours source subscription requested");
    }

    public KisRealtimeSubscriptionRequestResult unsubscribeRealAfterHours(String stockCode) {
        ensureStockExists(stockCode);
        if (!hasRealKisCredential()) {
            return new KisRealtimeSubscriptionRequestResult(
                    stockCode,
                    AFTER_HOURS_REAL,
                    "DISABLED",
                    "Real KIS credential is not configured");
        }
        String activeStockCode = activeAfterHoursStockCode.get();
        if (!stockCode.equals(activeStockCode)) {
            return new KisRealtimeSubscriptionRequestResult(
                    stockCode,
                    AFTER_HOURS_REAL,
                    "UNCHANGED",
                    "Requested stock is not the active real after-hours subscription");
        }
        String approvalKey = realApprovalKeyProvider.approvalKey();
        activeAfterHoursStockCode.compareAndSet(stockCode, "");
        realAfterHoursConnection.unsubscribe(List.of(frame(
                approvalKey,
                KisRealtimeTransaction.AFTER_HOURS_TRADE,
                KisRealtimeSubscriptionType.UNSUBSCRIBE,
                stockCode)));
        return new KisRealtimeSubscriptionRequestResult(
                stockCode,
                AFTER_HOURS_REAL,
                "UNSUBSCRIBED",
                "Real KIS after-hours source unsubscribe requested");
    }

    private void startRealAfterHoursConnectionIfNeeded() {
        if (realAfterHoursConnected.compareAndSet(false, true)) {
            realAfterHoursConnection.connect(
                    realKisProperties.websocketUrl(),
                    List.of(),
                    ingestionService::ingestKisMessage);
        }
    }

    private KisRealtimeSubscriptionFrame frame(
            String approvalKey,
            KisRealtimeTransaction transaction,
            KisRealtimeSubscriptionType subscriptionType,
            String stockCode) {
        return frameFactory.create(approvalKey, transaction, subscriptionType, stockCode);
    }

    private void ensureStockExists(String stockCode) {
        if (stockMasterRepository.findByCode(stockCode).isEmpty()) {
            throw new StockMasterNotFoundException(stockCode);
        }
    }

    private boolean hasRealKisCredential() {
        return StringUtils.hasText(realKisProperties.appKey())
                && StringUtils.hasText(realKisProperties.appSecret());
    }
}

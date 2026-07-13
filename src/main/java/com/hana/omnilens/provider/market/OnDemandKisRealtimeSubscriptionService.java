package com.hana.omnilens.provider.market;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import com.hana.omnilens.config.ExternalProviderProperties;
import com.hana.omnilens.market.application.RealtimeMarketDataIngestionService;
import com.hana.omnilens.market.application.KisRealtimeDynamicSubscriptionResult;
import com.hana.omnilens.market.application.KisRealtimeSessionRunner;
import com.hana.omnilens.market.application.KisRealtimeSessionRunner.KisRealtimeUnsubscribeResult;
import com.hana.omnilens.market.application.StockMasterNotFoundException;
import com.hana.omnilens.market.application.StockMasterRepository;
import com.hana.omnilens.provider.ExternalProviderResiliencePolicy;

@Service
public class OnDemandKisRealtimeSubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(OnDemandKisRealtimeSubscriptionService.class);
    private static final String REGULAR = "REGULAR";
    private static final String AFTER_HOURS_REAL = "AFTER_HOURS_REAL";

    private final KisRealtimeSessionRunner regularSessionRunner;
    private final KisRealtimeApprovalKeyProvider realApprovalKeyProvider;
    private final KisRealtimeSubscriptionFrameFactory frameFactory;
    private final ExternalProviderProperties.Kis realKisProperties;
    private final KisRealtimeWebSocketConnection realAfterHoursConnection;
    private final RealtimeMarketDataIngestionService ingestionService;
    private final StockMasterRepository stockMasterRepository;
    private final AtomicReference<String> activeAfterHoursStockCode = new AtomicReference<>("");
    private final AtomicBoolean realAfterHoursConnected = new AtomicBoolean(false);

    public OnDemandKisRealtimeSubscriptionService(
            KisRealtimeSessionRunner regularSessionRunner,
            KisRealtimeSubscriptionFrameFactory frameFactory,
            ExternalProviderProperties properties,
            ExternalProviderResiliencePolicy resiliencePolicy,
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            RealtimeMarketDataIngestionService ingestionService,
            StockMasterRepository stockMasterRepository) {
        this.regularSessionRunner = regularSessionRunner;
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
        try {
            KisRealtimeDynamicSubscriptionResult result = regularSessionRunner.subscribeStockCodes(List.of(stockCode));
            if (!result.enabled()) {
                return new KisRealtimeSubscriptionRequestResult(
                        stockCode,
                        REGULAR,
                        "DISABLED",
                        "KIS regular realtime source is disabled");
            }
            if (result.rejectedStockCodes().contains(stockCode)) {
                return new KisRealtimeSubscriptionRequestResult(
                        stockCode,
                        REGULAR,
                        "REJECTED",
                        "KIS realtime subscription capacity is unavailable");
            }
            String message = result.rotatedOutStockCodes().isEmpty()
                    ? "KIS regular realtime source subscription is active"
                    : "KIS regular realtime source subscription rotated within the provider limit";
            return new KisRealtimeSubscriptionRequestResult(
                    stockCode,
                    REGULAR,
                    result.subscribedStockCodes().contains(stockCode) ? "SUBSCRIBED" : "UNCHANGED",
                    message);
        } catch (RuntimeException exception) {
            return providerUnavailableResult(stockCode, REGULAR, "subscribe", exception);
        }
    }

    public KisRealtimeSubscriptionRequestResult unsubscribeRegular(String stockCode) {
        ensureStockExists(stockCode);
        try {
            KisRealtimeUnsubscribeResult result = regularSessionRunner.unsubscribeStockCode(stockCode);
            if (result == KisRealtimeUnsubscribeResult.DISABLED) {
                return new KisRealtimeSubscriptionRequestResult(
                        stockCode,
                        REGULAR,
                        "DISABLED",
                        "KIS regular realtime source is disabled");
            }
            if (result == KisRealtimeUnsubscribeResult.PINNED) {
                return new KisRealtimeSubscriptionRequestResult(
                        stockCode,
                        REGULAR,
                        "UNCHANGED",
                        "KIS regular realtime source is pinned by the default universe");
            }
            return new KisRealtimeSubscriptionRequestResult(
                    stockCode,
                    REGULAR,
                    result == KisRealtimeUnsubscribeResult.UNSUBSCRIBED ? "UNSUBSCRIBED" : "UNCHANGED",
                    result == KisRealtimeUnsubscribeResult.UNSUBSCRIBED
                            ? "KIS regular realtime source subscription released"
                            : "KIS regular realtime source subscription was not active");
        } catch (RuntimeException exception) {
            return providerUnavailableResult(stockCode, REGULAR, "unsubscribe", exception);
        }
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
        try {
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
        } catch (RuntimeException exception) {
            return providerUnavailableResult(stockCode, AFTER_HOURS_REAL, "subscribe", exception);
        }
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
        try {
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
        } catch (RuntimeException exception) {
            return providerUnavailableResult(stockCode, AFTER_HOURS_REAL, "unsubscribe", exception);
        }
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

    private KisRealtimeSubscriptionRequestResult providerUnavailableResult(
            String stockCode,
            String session,
            String action,
            RuntimeException exception) {
        log.warn("KIS realtime source {} rejected stockCode={} session={}: {}",
                action,
                stockCode,
                session,
                exception.toString());
        return new KisRealtimeSubscriptionRequestResult(
                stockCode,
                session,
                "REJECTED",
                "KIS realtime source is temporarily unavailable");
    }
}

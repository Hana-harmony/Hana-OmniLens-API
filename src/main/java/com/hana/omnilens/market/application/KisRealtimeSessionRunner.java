package com.hana.omnilens.market.application;

import java.time.Clock;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.hana.omnilens.config.ExternalProviderProperties;
import com.hana.omnilens.config.KisRealtimeProperties;
import com.hana.omnilens.market.domain.StockSummary;
import com.hana.omnilens.provider.market.KisRealtimeApprovalKeyProvider;
import com.hana.omnilens.provider.market.KisRealtimeSubscriptionFrame;
import com.hana.omnilens.provider.market.KisRealtimeSubscriptionFrameFactory;
import com.hana.omnilens.provider.market.KisRealtimeSubscriptionType;
import com.hana.omnilens.provider.market.KisRealtimeTransaction;
import com.hana.omnilens.provider.market.KisRealtimeWebSocketConnection;

@Component
public class KisRealtimeSessionRunner {

    private static final Logger log = LoggerFactory.getLogger(KisRealtimeSessionRunner.class);
    private static final int KIS_APP_KEY_SUBSCRIPTION_LIMIT = 40;
    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");
    private static final LocalTime AFTER_HOURS_OPEN = LocalTime.of(16, 0);
    private static final LocalTime AFTER_HOURS_CLOSE = LocalTime.of(18, 0);

    private final KisRealtimeProperties kisRealtimeProperties;
    private final ExternalProviderProperties externalProviderProperties;
    private final KisRealtimeSubscriptionFrameFactory frameFactory;
    private final KisRealtimeWebSocketConnection webSocketConnection;
    private final RealtimeMarketDataIngestionService ingestionService;
    private final KisRealtimeApprovalKeyProvider approvalKeyProvider;
    private final StockMasterRepository stockMasterRepository;
    private final Clock clock;
    private final Set<String> activeStockCodes = ConcurrentHashMap.newKeySet();
    private final Set<String> activeSubscriptionFrameKeys = ConcurrentHashMap.newKeySet();
    private volatile String activeApprovalKey;

    @Autowired
    public KisRealtimeSessionRunner(
            KisRealtimeProperties kisRealtimeProperties,
            ExternalProviderProperties externalProviderProperties,
            KisRealtimeSubscriptionFrameFactory frameFactory,
            KisRealtimeWebSocketConnection webSocketConnection,
            RealtimeMarketDataIngestionService ingestionService,
            KisRealtimeApprovalKeyProvider approvalKeyProvider,
            StockMasterRepository stockMasterRepository) {
        this(
                kisRealtimeProperties,
                externalProviderProperties,
                frameFactory,
                webSocketConnection,
                ingestionService,
                approvalKeyProvider,
                stockMasterRepository,
                Clock.system(KOREA_ZONE));
    }

    KisRealtimeSessionRunner(
            KisRealtimeProperties kisRealtimeProperties,
            ExternalProviderProperties externalProviderProperties,
            KisRealtimeSubscriptionFrameFactory frameFactory,
            KisRealtimeWebSocketConnection webSocketConnection,
            RealtimeMarketDataIngestionService ingestionService,
            KisRealtimeApprovalKeyProvider approvalKeyProvider,
            StockMasterRepository stockMasterRepository,
            Clock clock) {
        this.kisRealtimeProperties = kisRealtimeProperties;
        this.externalProviderProperties = externalProviderProperties;
        this.frameFactory = frameFactory;
        this.webSocketConnection = webSocketConnection;
        this.ingestionService = ingestionService;
        this.approvalKeyProvider = approvalKeyProvider;
        this.stockMasterRepository = stockMasterRepository;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!kisRealtimeProperties.enabled()) {
            log.info("KIS realtime session runner is disabled");
            return;
        }
        List<String> stockCodes = realtimeStockCodes();
        List<String> indexCodes = kisRealtimeProperties.indexCodes();
        if (stockCodes.isEmpty() && indexCodes.isEmpty()) {
            log.warn("KIS realtime session runner is enabled but realtime universe is empty");
            return;
        }
        try {
            String approvalKey = approvalKeyProvider.approvalKey();
            List<KisRealtimeSubscriptionFrame> frames = subscriptionFrames(approvalKey, stockCodes, indexCodes);
            this.activeApprovalKey = approvalKey;
            this.activeStockCodes.addAll(stockCodesForFrames(frames));
            this.activeSubscriptionFrameKeys.addAll(frameKeys(frames));
            log.info(
                    "Starting KIS realtime session stockCount={} indexCount={} subscriptionFrameCount={} subscriptionLimit={} orderBookEnabled={} afterHoursEnabled={} marketSession={}",
                    stockCodes.size(),
                    indexCodes.size(),
                    frames.size(),
                    subscriptionFrameLimit(),
                    kisRealtimeProperties.orderBookEnabled(),
                    kisRealtimeProperties.afterHoursEnabled(),
                    currentMarketSession());
            webSocketConnection.connect(
                    externalProviderProperties.kis().websocketUrl(),
                    frames,
                    ingestionService::ingestKisMessage);
        } catch (RuntimeException exception) {
            // 외부 승인키/웹소켓 장애가 API 기동 실패로 전파되지 않게 한다.
            log.warn("KIS realtime session start failed: {}", exception.toString());
        }
    }

    public KisRealtimeDynamicSubscriptionResult subscribeStockCodes(List<String> requestedStockCodes) {
        if (!kisRealtimeProperties.enabled()) {
            return disabledResult();
        }
        List<String> normalizedStockCodes = normalizeStockCodes(requestedStockCodes);
        if (normalizedStockCodes.isEmpty()) {
            return new KisRealtimeDynamicSubscriptionResult(
                    true,
                    subscriptionFrameLimit(),
                    activeSubscriptionFrameCount(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of());
        }
        String approvalKey = activeApprovalKey;
        if (approvalKey == null || approvalKey.isBlank()) {
            approvalKey = approvalKeyProvider.approvalKey();
            activeApprovalKey = approvalKey;
        }

        List<String> subscribed = new ArrayList<>();
        List<String> alreadySubscribed = new ArrayList<>();
        List<String> unsupported = new ArrayList<>();
        List<String> rejected = new ArrayList<>();
        List<KisRealtimeSubscriptionFrame> newFrames = new ArrayList<>();
        for (String stockCode : normalizedStockCodes) {
            if (activeStockCodes.contains(stockCode)) {
                alreadySubscribed.add(stockCode);
                continue;
            }
            if (stockMasterRepository.findByCode(stockCode).isEmpty()) {
                unsupported.add(stockCode);
                continue;
            }
            List<KisRealtimeSubscriptionFrame> frames = subscriptionFrames(approvalKey, List.of(stockCode), List.of());
            if (activeSubscriptionFrameCount() + newFrames.size() + frames.size() > subscriptionFrameLimit()) {
                rejected.add(stockCode);
                continue;
            }
            newFrames.addAll(frames);
            subscribed.add(stockCode);
        }
        if (!newFrames.isEmpty()) {
            webSocketConnection.subscribe(newFrames);
            activeStockCodes.addAll(subscribed);
            activeSubscriptionFrameKeys.addAll(frameKeys(newFrames));
        }
        return new KisRealtimeDynamicSubscriptionResult(
                true,
                subscriptionFrameLimit(),
                activeSubscriptionFrameCount(),
                subscribed,
                alreadySubscribed,
                unsupported,
                rejected);
    }

    List<KisRealtimeSubscriptionFrame> subscriptionFrames(String approvalKey, List<String> stockCodes) {
        return subscriptionFrames(approvalKey, stockCodes, kisRealtimeProperties.indexCodes());
    }

    List<KisRealtimeSubscriptionFrame> subscriptionFrames(
            String approvalKey,
            List<String> stockCodes,
            List<String> indexCodes) {
        List<KisRealtimeSubscriptionFrame> frames = new ArrayList<>();
        int subscriptionFrameLimit = subscriptionFrameLimit();
        MarketSession marketSession = currentMarketSession();
        for (String stockCode : stockCodes) {
            if (frames.size() >= subscriptionFrameLimit) {
                break;
            }
            if (marketSession == MarketSession.AFTER_HOURS) {
                frames.add(frameFactory.create(
                        approvalKey,
                        KisRealtimeTransaction.AFTER_HOURS_TRADE,
                        KisRealtimeSubscriptionType.SUBSCRIBE,
                        stockCode));
                if (kisRealtimeProperties.orderBookEnabled() && frames.size() < subscriptionFrameLimit) {
                    frames.add(frameFactory.create(
                            approvalKey,
                            KisRealtimeTransaction.AFTER_HOURS_ORDERBOOK,
                            KisRealtimeSubscriptionType.SUBSCRIBE,
                            stockCode));
                }
                continue;
            }
            frames.add(frameFactory.create(
                    approvalKey,
                    KisRealtimeTransaction.TRADE,
                    KisRealtimeSubscriptionType.SUBSCRIBE,
                    stockCode));
            if (kisRealtimeProperties.orderBookEnabled() && frames.size() < subscriptionFrameLimit) {
                frames.add(frameFactory.create(
                        approvalKey,
                        KisRealtimeTransaction.ORDERBOOK,
                        KisRealtimeSubscriptionType.SUBSCRIBE,
                        stockCode));
            }
        }
        for (String indexCode : indexCodes) {
            if (frames.size() >= subscriptionFrameLimit) {
                break;
            }
            frames.add(frameFactory.create(
                    approvalKey,
                    KisRealtimeTransaction.INDEX_TRADE,
                    KisRealtimeSubscriptionType.SUBSCRIBE,
                    indexCode));
        }
        return List.copyOf(frames);
    }

    private MarketSession currentMarketSession() {
        if (!kisRealtimeProperties.afterHoursEnabled()) {
            return MarketSession.REGULAR;
        }
        LocalTime now = ZonedDateTime.now(clock).withZoneSameInstant(KOREA_ZONE).toLocalTime();
        if (!now.isBefore(AFTER_HOURS_OPEN) && now.isBefore(AFTER_HOURS_CLOSE)) {
            return MarketSession.AFTER_HOURS;
        }
        return MarketSession.REGULAR;
    }

    private List<String> realtimeStockCodes() {
        if (!kisRealtimeProperties.stockCodes().isEmpty()) {
            return kisRealtimeProperties.stockCodes();
        }
        return stockMasterRepository.findAll(kisRealtimeProperties.stockLimit())
                .stream()
                .map(StockSummary::stockCode)
                .toList();
    }

    private int subscriptionFrameLimit() {
        return Math.min(kisRealtimeProperties.shardSize(), KIS_APP_KEY_SUBSCRIPTION_LIMIT);
    }

    private int activeSubscriptionFrameCount() {
        return activeSubscriptionFrameKeys.size();
    }

    private List<String> normalizeStockCodes(List<String> requestedStockCodes) {
        if (requestedStockCodes == null) {
            return List.of();
        }
        return requestedStockCodes.stream()
                .filter(stockCode -> stockCode != null && stockCode.matches("\\d{6}"))
                .collect(java.util.stream.Collectors.collectingAndThen(
                        java.util.stream.Collectors.toCollection(LinkedHashSet::new),
                        List::copyOf));
    }

    private List<String> stockCodesForFrames(List<KisRealtimeSubscriptionFrame> frames) {
        return frames.stream()
                .map(frame -> frame.body().input().trKey())
                .filter(trKey -> trKey != null && trKey.matches("\\d{6}"))
                .distinct()
                .toList();
    }

    private List<String> frameKeys(List<KisRealtimeSubscriptionFrame> frames) {
        return frames.stream()
                .map(this::frameKey)
                .toList();
    }

    private String frameKey(KisRealtimeSubscriptionFrame frame) {
        return frame.body().input().trId() + ":" + frame.body().input().trKey();
    }

    private KisRealtimeDynamicSubscriptionResult disabledResult() {
        return new KisRealtimeDynamicSubscriptionResult(
                false,
                subscriptionFrameLimit(),
                activeSubscriptionFrameCount(),
                List.of(),
                List.of(),
                List.of(),
                List.of());
    }

    private enum MarketSession {
        REGULAR,
        AFTER_HOURS
    }
}

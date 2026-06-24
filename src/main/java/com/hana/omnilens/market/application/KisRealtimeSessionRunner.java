package com.hana.omnilens.market.application;

import java.util.ArrayList;
import java.time.Clock;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.hana.omnilens.config.ExternalProviderProperties;
import com.hana.omnilens.config.KisRealtimeProperties;
import com.hana.omnilens.market.domain.StockSummary;
import com.hana.omnilens.provider.market.KisRealtimeSubscriptionFrame;
import com.hana.omnilens.provider.market.KisRealtimeSubscriptionFrameFactory;
import com.hana.omnilens.provider.market.KisRealtimeSubscriptionType;
import com.hana.omnilens.provider.market.KisRealtimeTransaction;
import com.hana.omnilens.provider.market.KisRealtimeWebSocketConnection;
import com.hana.omnilens.provider.market.KisRealtimeApprovalKeyProvider;

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
        if (stockCodes.isEmpty()) {
            log.warn("KIS realtime session runner is enabled but realtime stock universe is empty");
            return;
        }
        try {
            String approvalKey = approvalKeyProvider.approvalKey();
            List<KisRealtimeSubscriptionFrame> frames = subscriptionFrames(approvalKey, stockCodes);
            log.info(
                    "Starting KIS realtime session stockCount={} subscriptionFrameCount={} subscriptionLimit={} orderBookEnabled={} afterHoursEnabled={} marketSession={}",
                    stockCodes.size(),
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

    List<KisRealtimeSubscriptionFrame> subscriptionFrames(String approvalKey, List<String> stockCodes) {
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

    private enum MarketSession {
        REGULAR,
        AFTER_HOURS
    }
}

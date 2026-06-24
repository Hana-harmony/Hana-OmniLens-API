package com.hana.omnilens.provider.market;

import java.util.regex.Pattern;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class KisRealtimeSubscriptionFrameFactory {

    private static final Pattern STOCK_CODE_PATTERN = Pattern.compile("\\d{6}");

    public KisRealtimeSubscriptionFrame create(
            String approvalKey,
            KisRealtimeTransaction transaction,
            KisRealtimeSubscriptionType subscriptionType,
            String stockCode) {
        if (!StringUtils.hasText(approvalKey)) {
            throw new IllegalArgumentException("approvalKey is required");
        }
        if (!STOCK_CODE_PATTERN.matcher(stockCode).matches()) {
            throw new IllegalArgumentException("stockCode must be six digits");
        }
        return new KisRealtimeSubscriptionFrame(
                new KisRealtimeSubscriptionFrame.Header(
                        approvalKey,
                        subscriptionType.code(),
                        "P",
                        "utf-8"),
                new KisRealtimeSubscriptionFrame.Body(
                        new KisRealtimeSubscriptionFrame.Input(
                                transaction.trId(),
                                stockCode)));
    }
}

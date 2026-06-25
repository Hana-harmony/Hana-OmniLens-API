package com.hana.omnilens.provider.market;

import java.util.regex.Pattern;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class KisRealtimeSubscriptionFrameFactory {

    private static final Pattern STOCK_CODE_PATTERN = Pattern.compile("\\d{6}");
    private static final Pattern INDEX_CODE_PATTERN = Pattern.compile("\\d{4}");

    public KisRealtimeSubscriptionFrame create(
            String approvalKey,
            KisRealtimeTransaction transaction,
            KisRealtimeSubscriptionType subscriptionType,
            String stockCode) {
        if (!StringUtils.hasText(approvalKey)) {
            throw new IllegalArgumentException("approvalKey is required");
        }
        if (!isValidTransactionKey(transaction, stockCode)) {
            throw new IllegalArgumentException("stockCode or indexCode format is invalid");
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

    private boolean isValidTransactionKey(KisRealtimeTransaction transaction, String key) {
        if (transaction == KisRealtimeTransaction.INDEX_TRADE) {
            return INDEX_CODE_PATTERN.matcher(key).matches();
        }
        return STOCK_CODE_PATTERN.matcher(key).matches();
    }
}

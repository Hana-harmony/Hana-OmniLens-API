package com.hana.omniconnect.provider.market;

import java.net.URI;
import java.util.List;
import java.util.function.Consumer;

public interface KisRealtimeWebSocketConnection {

    void connect(
            URI websocketUrl,
            List<KisRealtimeSubscriptionFrame> subscriptionFrames,
            Consumer<String> messageConsumer);

    default void subscribe(List<KisRealtimeSubscriptionFrame> subscriptionFrames) {
    }

    default void unsubscribe(List<KisRealtimeSubscriptionFrame> subscriptionFrames) {
    }
}

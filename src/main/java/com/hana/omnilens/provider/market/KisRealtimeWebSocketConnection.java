package com.hana.omnilens.provider.market;

import java.net.URI;
import java.util.List;
import java.util.function.Consumer;

public interface KisRealtimeWebSocketConnection {

    void connect(
            URI websocketUrl,
            List<KisRealtimeSubscriptionFrame> subscriptionFrames,
            Consumer<String> messageConsumer);

    void send(List<KisRealtimeSubscriptionFrame> subscriptionFrames);
}

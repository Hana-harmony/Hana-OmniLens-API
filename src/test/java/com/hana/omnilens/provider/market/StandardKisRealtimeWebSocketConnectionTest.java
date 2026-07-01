package com.hana.omnilens.provider.market;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StandardKisRealtimeWebSocketConnectionTest {

    @Test
    void reconnectDelayStaysWithinFastRecoveryWindow() {
        for (int attempt = 0; attempt <= 20; attempt++) {
            long delaySeconds = StandardKisRealtimeWebSocketConnection.reconnectDelaySeconds(attempt);

            assertThat(delaySeconds).isBetween(1L, 30L);
        }
    }
}

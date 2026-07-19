package com.hana.omniconnect.provider.market;

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

    @Test
    void sanitizeControlMessageTextMasksApprovalLikeUuid() {
        String sanitized = StandardKisRealtimeWebSocketConnection.sanitizeControlMessageText(
                "invalid approval : bb00bc18-d895-47fd-8bc8-25be3e54bcdb");

        assertThat(sanitized).isEqualTo("invalid approval : ***");
    }
}

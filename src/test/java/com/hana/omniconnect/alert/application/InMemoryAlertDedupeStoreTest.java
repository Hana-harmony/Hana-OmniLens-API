package com.hana.omniconnect.alert.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class InMemoryAlertDedupeStoreTest {

    @Test
    void leaseCanOnlyBeReleasedByItsOwnerToken() {
        InMemoryAlertDedupeStore store = new InMemoryAlertDedupeStore(10);
        String token = store.acquireLease("stock", Duration.ofHours(1)).orElseThrow();

        store.releaseLease("stock", "different-token");
        assertThat(store.acquireLease("stock", Duration.ofHours(1))).isEmpty();

        store.releaseLease("stock", token);
        assertThat(store.acquireLease("stock", Duration.ofHours(1))).isPresent();
    }

    @Test
    void markIfFirstRejectsDuplicateAndEvictsOldestKey() {
        InMemoryAlertDedupeStore store = new InMemoryAlertDedupeStore(2);

        assertThat(store.markIfFirst("a")).isTrue();
        assertThat(store.markIfFirst("a")).isFalse();
        assertThat(store.markIfFirst("b")).isTrue();
        assertThat(store.markIfFirst("c")).isTrue();
        assertThat(store.markIfFirst("a")).isTrue();
    }

    @Test
    void removeAllowsRetryAfterAnalysisFailure() {
        InMemoryAlertDedupeStore store = new InMemoryAlertDedupeStore(2);

        assertThat(store.markIfFirst("source")).isTrue();
        store.remove("source");

        assertThat(store.markIfFirst("source")).isTrue();
    }
}

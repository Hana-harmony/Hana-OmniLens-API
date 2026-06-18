package com.hana.omnilens.alert.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class InMemoryAlertDedupeStoreTest {

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

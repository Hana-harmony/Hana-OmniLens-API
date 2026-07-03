package com.hana.omnilens.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AlertCollectionSchedulerPropertiesTest {

    @Test
    void defaultsAreDisabledAndBounded() {
        AlertCollectionSchedulerProperties properties =
                new AlertCollectionSchedulerProperties(false, 0, 0, 0, null, false, "", 0, false, 0);

        assertThat(properties.enabled()).isFalse();
        assertThat(properties.fixedDelayMs()).isEqualTo(300_000L);
        assertThat(properties.newsDisplay()).isEqualTo(10);
        assertThat(properties.disclosureLookbackDays()).isEqualTo(7);
        assertThat(properties.watchlists()).isEmpty();
        assertThat(properties.defaultUniverseEnabled()).isFalse();
        assertThat(properties.defaultUniversePartnerId()).isEqualTo("omnilens-default-universe");
        assertThat(properties.priorityStockLimit()).isEqualTo(30);
        assertThat(properties.includeForeignOwnershipRestrictedStocks()).isFalse();
        assertThat(properties.collectionBatchSize()).isEqualTo(20);
    }

    @Test
    void canonicalDefaultsAreBoundedForSchedulerRuntime() {
        AlertCollectionSchedulerProperties properties =
                new AlertCollectionSchedulerProperties(true, 0, 0, 0, null, true, "", 0, true, 100);

        assertThat(properties.defaultUniverseEnabled()).isTrue();
        assertThat(properties.defaultUniversePartnerId()).isEqualTo("omnilens-default-universe");
        assertThat(properties.priorityStockLimit()).isEqualTo(30);
        assertThat(properties.includeForeignOwnershipRestrictedStocks()).isTrue();
        assertThat(properties.collectionBatchSize()).isEqualTo(20);
    }
}

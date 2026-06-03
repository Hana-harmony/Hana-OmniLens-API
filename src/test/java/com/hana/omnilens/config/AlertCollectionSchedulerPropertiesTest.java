package com.hana.omnilens.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AlertCollectionSchedulerPropertiesTest {

    @Test
    void defaultsAreDisabledAndBounded() {
        AlertCollectionSchedulerProperties properties =
                new AlertCollectionSchedulerProperties(false, 0, 0, 0, null);

        assertThat(properties.enabled()).isFalse();
        assertThat(properties.fixedDelayMs()).isEqualTo(300_000L);
        assertThat(properties.newsDisplay()).isEqualTo(10);
        assertThat(properties.disclosureLookbackDays()).isEqualTo(7);
        assertThat(properties.watchlists()).isEmpty();
    }
}

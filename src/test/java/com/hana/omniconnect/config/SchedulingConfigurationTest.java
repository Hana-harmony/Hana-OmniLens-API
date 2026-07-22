package com.hana.omniconnect.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

class SchedulingConfigurationTest {

    @Test
    void createsDedicatedFourThreadScheduler() {
        ThreadPoolTaskScheduler scheduler = new SchedulingConfiguration().taskScheduler();

        assertThat(scheduler.getPoolSize()).isEqualTo(4);
        assertThat(scheduler.getThreadNamePrefix()).isEqualTo("omni-connect-scheduler-");
    }

    @Test
    void createsSingleThreadDisclosureScheduler() {
        ThreadPoolTaskScheduler scheduler = new SchedulingConfiguration().disclosureTaskScheduler();

        assertThat(scheduler.getPoolSize()).isEqualTo(1);
        assertThat(scheduler.getThreadNamePrefix()).isEqualTo("omni-connect-disclosure-");
    }

    @Test
    void createsTwoBoundedNewsProcessingWorkers() {
        ThreadPoolTaskExecutor executor = new SchedulingConfiguration().newsProcessingExecutor();

        assertThat(executor.getCorePoolSize()).isEqualTo(2);
        assertThat(executor.getMaxPoolSize()).isEqualTo(2);
        assertThat(executor.getThreadNamePrefix()).isEqualTo("omni-connect-news-processing-");
        executor.shutdown();
    }

    @Test
    void createsEightAlertCollectionWorkers() {
        ThreadPoolTaskExecutor executor = new SchedulingConfiguration().alertCollectionExecutor();

        assertThat(executor.getCorePoolSize()).isEqualTo(8);
        assertThat(executor.getMaxPoolSize()).isEqualTo(8);
        assertThat(executor.getThreadNamePrefix()).isEqualTo("omni-connect-alert-collection-");
        executor.shutdown();
    }
}

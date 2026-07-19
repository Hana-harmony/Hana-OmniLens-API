package com.hana.omniconnect.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

class SchedulingConfigurationTest {

    @Test
    void createsDedicatedFourThreadScheduler() {
        ThreadPoolTaskScheduler scheduler = new SchedulingConfiguration().taskScheduler();

        assertThat(scheduler.getPoolSize()).isEqualTo(4);
        assertThat(scheduler.getThreadNamePrefix()).isEqualTo("omni-connect-scheduler-");
    }
}

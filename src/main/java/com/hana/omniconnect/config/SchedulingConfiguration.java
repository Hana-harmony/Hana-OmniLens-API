package com.hana.omniconnect.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class SchedulingConfiguration {

    @Bean(name = "taskScheduler")
    @Primary
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4);
        scheduler.setThreadNamePrefix("omni-connect-scheduler-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        // 웹소켓 브로커 스케줄러와 분리해 장시간 수집 작업이 다른 주기를 막지 않게 한다.
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }

    @Bean(name = "disclosureTaskScheduler")
    public ThreadPoolTaskScheduler disclosureTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("omni-connect-disclosure-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        // 장시간 뉴스·시세 수집이 공시 큐 처리를 점유하지 못하게 전용 실행기를 사용한다.
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }

}

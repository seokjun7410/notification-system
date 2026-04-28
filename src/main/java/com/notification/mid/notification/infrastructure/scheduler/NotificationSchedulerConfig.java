package com.notification.mid.notification.infrastructure.scheduler;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class NotificationSchedulerConfig {

    @Bean(name = "notificationWorkerTaskScheduler")
    ThreadPoolTaskScheduler notificationWorkerTaskScheduler() {
        return createScheduler("notification-worker-");
    }

    @Bean(name = "notificationRecoveryTaskScheduler")
    ThreadPoolTaskScheduler notificationRecoveryTaskScheduler() {
        return createScheduler("notification-recovery-");
    }

    private ThreadPoolTaskScheduler createScheduler(String threadNamePrefix) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix(threadNamePrefix);
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(10);
        scheduler.initialize();
        return scheduler;
    }
}

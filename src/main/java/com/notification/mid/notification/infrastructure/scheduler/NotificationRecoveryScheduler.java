package com.notification.mid.notification.infrastructure.scheduler;

import com.notification.mid.notification.application.port.in.NotificationRecoveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component
public class NotificationRecoveryScheduler {

    private final NotificationRecoveryService notificationRecoveryService;

    @Value("${notification.scheduler.recovery.batch-size:100}")
    private int batchSize;

    @Scheduled(
            fixedDelayString = "${notification.scheduler.recovery.polling-delay-ms:60000}",
            scheduler = "notificationRecoveryTaskScheduler"
    )
    public void recover() {
        recoverOnce();
    }

    public int recoverOnce() {
        int recoveredCount = notificationRecoveryService.recoverStaleProcessingDispatches(batchSize);
        log.debug("dispatch 복구 스케줄러 실행이 완료되었습니다. recoveredCount={}", recoveredCount);
        return recoveredCount;
    }
}

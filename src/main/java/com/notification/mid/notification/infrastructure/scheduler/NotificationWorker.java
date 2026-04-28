package com.notification.mid.notification.infrastructure.scheduler;

import java.util.List;

import com.notification.mid.notification.application.dto.NotificationDispatchTargetDto;
import com.notification.mid.notification.application.port.in.NotificationDispatchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class NotificationWorker {

    private final NotificationDispatchService notificationDispatchService;
    private final int batchSize;

    public NotificationWorker(
            NotificationDispatchService notificationDispatchService,
            @Value("${notification.scheduler.worker.batch-size:50}") int batchSize
    ) {
        this.notificationDispatchService = notificationDispatchService;
        this.batchSize = batchSize;
    }

    @Scheduled(
            fixedDelayString = "${notification.scheduler.worker.polling-delay-ms:5000}",
            scheduler = "notificationWorkerTaskScheduler"
    )
    public void poll() {
        runOnce();
    }

    public int runOnce() {
        int processedCount = 0;

        while (processedCount < batchSize) {
            List<NotificationDispatchTargetDto> dispatchTargets = notificationDispatchService.claimDispatchTargets(1);

            if (dispatchTargets.isEmpty()) {
                break;
            }

            dispatchSafely(dispatchTargets.getFirst());
            processedCount++;
        }

        return processedCount;
    }

    private void dispatchSafely(NotificationDispatchTargetDto dispatchTarget) {
        try {
            notificationDispatchService.dispatch(dispatchTarget);
        } catch (Exception exception) {
            log.error("알림 발송 처리에 실패했습니다. dispatchId={}, notificationId={}",
                    dispatchTarget.dispatchId(), dispatchTarget.notificationId(), exception);
        }
    }
}

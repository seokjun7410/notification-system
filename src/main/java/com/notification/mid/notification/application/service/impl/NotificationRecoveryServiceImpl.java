package com.notification.mid.notification.application.service.impl;

import java.time.LocalDateTime;
import java.util.List;

import com.notification.mid.common.time.TimeProvider;
import com.notification.mid.notification.application.port.out.NotificationDispatchPollingPort;
import com.notification.mid.notification.application.port.in.NotificationRecoveryService;
import com.notification.mid.notification.application.service.processor.NotificationDispatchOutcomeProcessor;
import com.notification.mid.notification.domain.dispatch.NotificationDispatch;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Service
@Transactional(readOnly = true)
public class NotificationRecoveryServiceImpl implements NotificationRecoveryService {

    private static final String RECOVERY_MESSAGE = "오래된 PROCESSING 상태를 UNKNOWN으로 전환했습니다.";

    private final NotificationDispatchPollingPort notificationDispatchPollingPort;
    private final NotificationDispatchOutcomeProcessor notificationDispatchOutcomeProcessor;
    private final TimeProvider timeProvider;

    @Override
    @Transactional
    public int recoverStaleProcessingDispatches(int batchSize) {
        LocalDateTime now = timeProvider.now();
        LocalDateTime threshold = now.minusMinutes(5);
        List<NotificationDispatch> staleNotificationDispatches = notificationDispatchPollingPort.findStaleProcessingRecoveryTargets(
                threshold,
                batchSize
        );

        return (int) staleNotificationDispatches.stream()
                .filter(notificationDispatch -> notificationDispatchOutcomeProcessor
                        .recoverStaleProcessing(notificationDispatch, RECOVERY_MESSAGE, now))
                .count();
    }
}

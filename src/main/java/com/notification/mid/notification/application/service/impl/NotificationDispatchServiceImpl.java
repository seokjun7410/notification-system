package com.notification.mid.notification.application.service.impl;

import java.util.ArrayList;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.notification.mid.common.exception.shared.BusinessException;
import com.notification.mid.common.exception.shared.ErrorCode;
import com.notification.mid.common.time.TimeProvider;
import com.notification.mid.notification.application.dto.NotificationDispatchTargetDto;
import com.notification.mid.notification.application.exception.NotificationSendTimeoutException;
import com.notification.mid.notification.application.exception.NonRetryableNotificationSendException;
import com.notification.mid.notification.application.exception.RetryableNotificationSendException;
import com.notification.mid.notification.application.port.out.NotificationDispatchCommandPort;
import com.notification.mid.notification.application.port.out.NotificationDispatchPollingPort;
import com.notification.mid.notification.application.port.out.NotificationDispatchQueryPort;
import com.notification.mid.notification.application.port.out.NotificationSender;
import com.notification.mid.notification.application.port.in.NotificationDispatchService;
import com.notification.mid.notification.application.service.processor.NotificationDispatchOutcomeProcessor;
import com.notification.mid.notification.domain.dispatch.NotificationDispatch;
import com.notification.mid.notification.domain.dispatch.NotificationDispatchStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Service
@Transactional(readOnly = true)
public class NotificationDispatchServiceImpl implements NotificationDispatchService {

    private final NotificationDispatchQueryPort notificationDispatchQueryPort;
    private final NotificationDispatchCommandPort notificationDispatchCommandPort;
    private final NotificationDispatchPollingPort notificationDispatchPollingPort;
    private final NotificationSender notificationSender;
    private final NotificationDispatchOutcomeProcessor notificationDispatchOutcomeProcessor;
    private final TimeProvider timeProvider;

    @Override
    @Transactional
    public List<NotificationDispatchTargetDto> claimDispatchTargets(int batchSize) {
        LocalDateTime now = timeProvider.now();
        List<NotificationDispatch> notificationDispatches = notificationDispatchPollingPort.findDispatchTargets(now, batchSize);
        List<NotificationDispatchTargetDto> claimedDispatchTargets = new ArrayList<>();

        for (NotificationDispatch notificationDispatch : notificationDispatches) {
            NotificationDispatchTargetDto dispatchTarget = NotificationDispatchTargetDto.from(notificationDispatch);

            if (claimProcessing(notificationDispatch, now)) {
                claimedDispatchTargets.add(dispatchTarget);
            }
        }

        return claimedDispatchTargets;
    }

    @Override
    @Transactional
    public void dispatch(NotificationDispatchTargetDto dispatchTarget) {
        UUID dispatchId = dispatchTarget.dispatchId();
        NotificationDispatch notificationDispatch = notificationDispatchQueryPort.findById(dispatchId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_DISPATCH_NOT_FOUND));

        if (notificationDispatch.getStatus() != NotificationDispatchStatus.PROCESSING) {
            log.info("알림 발송 정보가 PROCESSING 상태가 아니어서 발송을 건너뜁니다. dispatchId={}, status={}",
                    dispatchId, notificationDispatch.getStatus());
            return;
        }

        LocalDateTime requestedAt = timeProvider.now();

        if (!notificationDispatchCommandPort.markSendStarted(dispatchId, requestedAt, requestedAt)) {
            log.info("알림 발송 시작 시각 기록이 경쟁으로 무시돼 발송을 건너뜁니다. dispatchId={}", dispatchId);
            return;
        }

        try {
            notificationSender.send(notificationDispatch.getNotification(), notificationDispatch);
            notificationDispatchOutcomeProcessor.handleSuccess(notificationDispatch, requestedAt);
        } catch (NotificationSendTimeoutException exception) {
            notificationDispatchOutcomeProcessor.handleTimeout(notificationDispatch, requestedAt, exception);
        } catch (RetryableNotificationSendException exception) {
            notificationDispatchOutcomeProcessor.handleRetryableFailure(notificationDispatch, requestedAt, exception);
        } catch (NonRetryableNotificationSendException exception) {
            notificationDispatchOutcomeProcessor.handleNonRetryableFailure(notificationDispatch, requestedAt, exception);
        } catch (Exception exception) {
            notificationDispatchOutcomeProcessor.handleNonRetryableFailure(notificationDispatch, requestedAt, exception);
        }
    }

    private boolean claimProcessing(NotificationDispatch notificationDispatch, LocalDateTime now) {
        NotificationDispatchStatus currentStatus = notificationDispatch.getStatus();
        currentStatus.assertCanChangeTo(NotificationDispatchStatus.PROCESSING);

        boolean applied = notificationDispatchCommandPort.applyStateChange(
                notificationDispatch.getId(),
                currentStatus,
                NotificationDispatchStatus.PROCESSING,
                notificationDispatch.getRetryCount(),
                null,
                null,
                null,
                now
        );

        if (!applied) {
            log.info("알림 발송 PROCESSING 점유가 경쟁으로 무시됐습니다. dispatchId={}, expectedStatus={}",
                    notificationDispatch.getId(), currentStatus);
        }

        return applied;
    }
}

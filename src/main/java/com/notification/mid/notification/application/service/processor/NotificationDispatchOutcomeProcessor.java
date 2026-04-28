package com.notification.mid.notification.application.service.processor;

import java.time.LocalDateTime;

import com.notification.mid.common.time.TimeProvider;
import com.notification.mid.notification.application.exception.NotificationSendTimeoutException;
import com.notification.mid.notification.application.policy.RetryPolicy;
import com.notification.mid.notification.application.port.out.NotificationAttemptRepositoryPort;
import com.notification.mid.notification.application.port.out.NotificationDispatchCommandPort;
import com.notification.mid.notification.domain.attempt.NotificationAttempt;
import com.notification.mid.notification.domain.dispatch.NotificationDispatch;
import com.notification.mid.notification.domain.dispatch.NotificationDispatchStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationDispatchOutcomeProcessor {

    private final NotificationAttemptRepositoryPort notificationAttemptRepository;
    private final NotificationDispatchCommandPort notificationDispatchCommandPort;
    private final RetryPolicy retryPolicy;
    private final TimeProvider timeProvider;

    /**
     * 발송 성공 후 시도 이력을 남기고 SUCCESS 전이를 반영한다.
     * dispatch 유스케이스에서 성공 분기 이후 순서를 이 메서드가 끝까지 책임진다.
     */
    public void handleSuccess(NotificationDispatch notificationDispatch, LocalDateTime requestedAt) {
        int attemptNo = nextAttemptNo(notificationDispatch);
        LocalDateTime respondedAt = timeProvider.now();

        boolean applied = applyStateChange(
                notificationDispatch,
                NotificationDispatchStatus.SUCCESS,
                notificationDispatch.getRetryCount(),
                null,
                null,
                respondedAt,
                "SUCCESS"
        );

        if (applied) {
            notificationAttemptRepository.save(
                    NotificationAttempt.success(notificationDispatch, attemptNo, requestedAt, respondedAt)
            );
        }
    }

    /**
     * timeout 발생 시 TIMEOUT attempt를 남기고 UNKNOWN으로 전환한다.
     * timeout은 재시도 정책 밖의 불확실 상태로 보고 retryCount는 올리지 않는다.
     */
    public void handleTimeout(
            NotificationDispatch notificationDispatch,
            LocalDateTime requestedAt,
            NotificationSendTimeoutException exception
    ) {
        int attemptNo = nextAttemptNo(notificationDispatch);
        LocalDateTime respondedAt = timeProvider.now();
        String errorMessage = resolveErrorMessage(exception);

        boolean applied = applyStateChange(
                notificationDispatch,
                NotificationDispatchStatus.UNKNOWN,
                notificationDispatch.getRetryCount(),
                null,
                errorMessage,
                respondedAt,
                "UNKNOWN"
        );

        if (applied) {
            notificationAttemptRepository.save(
                    NotificationAttempt.timeout(notificationDispatch, attemptNo, errorMessage, requestedAt, respondedAt)
            );
        }
    }

    /**
     * 재시도 가능한 실패를 기록하고 retry 정책에 따라 RETRY_WAIT 또는 FAILED로 마무리한다.
     */
    public void handleRetryableFailure(
            NotificationDispatch notificationDispatch,
            LocalDateTime requestedAt,
            Exception exception
    ) {
        int attemptNo = nextAttemptNo(notificationDispatch);
        LocalDateTime respondedAt = timeProvider.now();
        String errorMessage = resolveErrorMessage(exception);

        int nextRetryCount = notificationDispatch.getRetryCount() + 1;
        retryPolicy.nextRetryAt(nextRetryCount, respondedAt)
                .ifPresentOrElse(
                        nextRetryAt -> saveFailureAttemptIfStateApplied(
                                notificationDispatch,
                                attemptNo,
                                requestedAt,
                                respondedAt,
                                errorMessage,
                                applyStateChange(
                                        notificationDispatch,
                                        NotificationDispatchStatus.RETRY_WAIT,
                                        nextRetryCount,
                                        nextRetryAt,
                                        errorMessage,
                                        respondedAt,
                                        "RETRY_WAIT"
                                )
                        ),
                        () -> saveFailureAttemptIfStateApplied(
                                notificationDispatch,
                                attemptNo,
                                requestedAt,
                                respondedAt,
                                errorMessage,
                                applyStateChange(
                                        notificationDispatch,
                                        NotificationDispatchStatus.FAILED,
                                        nextRetryCount,
                                        null,
                                        errorMessage,
                                        respondedAt,
                                        "FAILED"
                                )
                        )
                );
    }

    /**
     * 재시도하면 안 되는 실패를 즉시 FAILED로 종료한다.
     * validation 오류, 수신자 상태 오류처럼 영구 실패 성격의 예외를 처리할 때 사용한다.
     */
    public void handleNonRetryableFailure(
            NotificationDispatch notificationDispatch,
            LocalDateTime requestedAt,
            Exception exception
    ) {
        int attemptNo = nextAttemptNo(notificationDispatch);
        LocalDateTime respondedAt = timeProvider.now();
        String errorMessage = resolveErrorMessage(exception);
        int nextRetryCount = notificationDispatch.getRetryCount() + 1;

        saveFailureAttemptIfStateApplied(
                notificationDispatch,
                attemptNo,
                requestedAt,
                respondedAt,
                errorMessage,
                applyStateChange(
                        notificationDispatch,
                        NotificationDispatchStatus.FAILED,
                        nextRetryCount,
                        null,
                        errorMessage,
                        respondedAt,
                        "FAILED"
                )
        );
    }

    /**
     * recovery가 stale PROCESSING row를 회수한다.
     * 아직 외부 호출을 시작하지 못한 건은 PENDING으로 되돌리고,
     * 이미 외부 호출에 들어간 건만 UNKNOWN으로 격리한다.
     */
    public boolean recoverStaleProcessing(
            NotificationDispatch notificationDispatch,
            String lastError,
            LocalDateTime recoveredAt
    ) {
        if (notificationDispatch.getSendStartedAt() == null) {
            return applyStateChange(
                    notificationDispatch,
                    NotificationDispatchStatus.PENDING,
                    notificationDispatch.getRetryCount(),
                    null,
                    null,
                    recoveredAt,
                    "PENDING"
            );
        }

        return applyStateChange(
                notificationDispatch,
                NotificationDispatchStatus.UNKNOWN,
                notificationDispatch.getRetryCount(),
                null,
                lastError,
                recoveredAt,
                "UNKNOWN"
        );
    }

    private void saveFailureAttemptIfStateApplied(
            NotificationDispatch notificationDispatch,
            int attemptNo,
            LocalDateTime requestedAt,
            LocalDateTime respondedAt,
            String errorMessage,
            boolean stateApplied
    ) {
        if (!stateApplied) {
            return;
        }

        notificationAttemptRepository.save(
                NotificationAttempt.failure(notificationDispatch, attemptNo, errorMessage, requestedAt, respondedAt)
        );
    }

    private boolean applyStateChange(
            NotificationDispatch notificationDispatch,
            NotificationDispatchStatus nextStatus,
            int retryCount,
            LocalDateTime nextRetryAt,
            String lastError,
            LocalDateTime updatedAt,
            String targetStatus
    ) {
        NotificationDispatchStatus expectedCurrentStatus = notificationDispatch.getStatus();
        expectedCurrentStatus.assertCanChangeTo(nextStatus);

        boolean applied = notificationDispatchCommandPort.applyStateChange(
                notificationDispatch.getId(),
                expectedCurrentStatus,
                nextStatus,
                retryCount,
                nextRetryAt,
                lastError,
                null,
                updatedAt
        );

        if (!applied) {
            log.info("알림 발송 {} 상태 적용이 경쟁으로 무시됐습니다. dispatchId={}, expectedStatus={}, nextStatus={}",
                    targetStatus, notificationDispatch.getId(), expectedCurrentStatus, nextStatus);
        }

        return applied;
    }

    private int nextAttemptNo(NotificationDispatch notificationDispatch) {
        return (int) notificationAttemptRepository.countByNotificationDispatchId(notificationDispatch.getId()) + 1;
    }

    private String resolveErrorMessage(Exception exception) {
        return exception.getMessage() == null
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
    }
}

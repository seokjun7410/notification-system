package com.notification.mid.notification.application.service.processor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import com.notification.mid.common.time.TimeProvider;
import com.notification.mid.notification.application.exception.NotificationSendTimeoutException;
import com.notification.mid.notification.application.policy.RetryPolicy;
import com.notification.mid.notification.application.port.out.NotificationAttemptRepositoryPort;
import com.notification.mid.notification.application.port.out.NotificationDispatchCommandPort;
import com.notification.mid.notification.domain.attempt.NotificationAttempt;
import com.notification.mid.notification.domain.attempt.NotificationAttemptResultStatus;
import com.notification.mid.notification.domain.dispatch.NotificationDispatch;
import com.notification.mid.notification.domain.dispatch.NotificationDispatchStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationDispatchOutcomeProcessorTest {

    @Mock
    private NotificationAttemptRepositoryPort notificationAttemptRepository;

    @Mock
    private NotificationDispatchCommandPort notificationDispatchCommandPort;

    @Mock
    private RetryPolicy retryPolicy;

    @Mock
    private TimeProvider timeProvider;

    @Mock
    private NotificationDispatch notificationDispatch;

    @InjectMocks
    private NotificationDispatchOutcomeProcessor notificationDispatchOutcomeProcessor;

    @Test
    @DisplayName("성공 처리 시 SUCCESS attempt를 남기고 SUCCESS 상태를 반영한다")
    void handlesSuccess() {
        UUID dispatchId = UUID.randomUUID();
        LocalDateTime requestedAt = LocalDateTime.of(2026, 4, 28, 10, 0);
        LocalDateTime respondedAt = LocalDateTime.of(2026, 4, 28, 10, 0, 3);

        // given: 첫 번째 성공 시도를 기록할 PROCESSING dispatch가 있다
        when(notificationDispatch.getId()).thenReturn(dispatchId);
        when(notificationDispatch.getStatus()).thenReturn(NotificationDispatchStatus.PROCESSING);
        when(notificationDispatch.getRetryCount()).thenReturn(0);
        when(notificationAttemptRepository.countByNotificationDispatchId(dispatchId)).thenReturn(0L);
        when(timeProvider.now()).thenReturn(respondedAt);
        when(notificationDispatchCommandPort.applyStateChange(
                dispatchId,
                NotificationDispatchStatus.PROCESSING,
                NotificationDispatchStatus.SUCCESS,
                0,
                null,
                null,
                null,
                respondedAt
        )).thenReturn(true);

        // when: 성공 결과를 후처리하면
        notificationDispatchOutcomeProcessor.handleSuccess(notificationDispatch, requestedAt);

        ArgumentCaptor<NotificationAttempt> attemptCaptor = ArgumentCaptor.forClass(NotificationAttempt.class);

        // then: SUCCESS attempt를 저장하고 SUCCESS 상태 변경을 수행한다
        verify(notificationAttemptRepository).save(attemptCaptor.capture());
        verify(notificationDispatchCommandPort).applyStateChange(
                dispatchId,
                NotificationDispatchStatus.PROCESSING,
                NotificationDispatchStatus.SUCCESS,
                0,
                null,
                null,
                null,
                respondedAt
        );

        NotificationAttempt savedAttempt = attemptCaptor.getValue();
        assertThat(savedAttempt.getNotificationDispatch()).isSameAs(notificationDispatch);
        assertThat(savedAttempt.getAttemptNo()).isEqualTo(1);
        assertThat(savedAttempt.getResultStatus()).isEqualTo(NotificationAttemptResultStatus.SUCCESS);
        assertThat(savedAttempt.getFailureMessage()).isNull();
        assertThat(savedAttempt.getRequestedAt()).isEqualTo(requestedAt);
        assertThat(savedAttempt.getRespondedAt()).isEqualTo(respondedAt);
    }

    @Test
    @DisplayName("성공 처리 중 상태 변경이 실패하면 SUCCESS attempt를 남기지 않는다")
    void doesNotSaveSuccessAttemptWhenStateChangeIsSkipped() {
        UUID dispatchId = UUID.randomUUID();
        LocalDateTime requestedAt = LocalDateTime.of(2026, 4, 28, 10, 0);
        LocalDateTime respondedAt = LocalDateTime.of(2026, 4, 28, 10, 0, 3);

        // given: 상태 반영을 다른 경쟁자에게 선점당한 PROCESSING dispatch가 있다
        when(notificationDispatch.getId()).thenReturn(dispatchId);
        when(notificationDispatch.getStatus()).thenReturn(NotificationDispatchStatus.PROCESSING);
        when(notificationDispatch.getRetryCount()).thenReturn(0);
        when(notificationAttemptRepository.countByNotificationDispatchId(dispatchId)).thenReturn(0L);
        when(timeProvider.now()).thenReturn(respondedAt);
        when(notificationDispatchCommandPort.applyStateChange(
                dispatchId,
                NotificationDispatchStatus.PROCESSING,
                NotificationDispatchStatus.SUCCESS,
                0,
                null,
                null,
                null,
                respondedAt
        )).thenReturn(false);

        // when: 성공 결과를 후처리하면
        notificationDispatchOutcomeProcessor.handleSuccess(notificationDispatch, requestedAt);

        // then: 상태 반영이 무시되면 SUCCESS attempt도 저장하지 않는다
        verify(notificationAttemptRepository, never()).save(any(NotificationAttempt.class));
        verify(notificationDispatchCommandPort).applyStateChange(
                dispatchId,
                NotificationDispatchStatus.PROCESSING,
                NotificationDispatchStatus.SUCCESS,
                0,
                null,
                null,
                null,
                respondedAt
        );
    }

    @Test
    @DisplayName("timeout 처리 시 TIMEOUT attempt를 남기고 UNKNOWN 상태를 반영한다")
    void handlesTimeout() {
        UUID dispatchId = UUID.randomUUID();
        LocalDateTime requestedAt = LocalDateTime.of(2026, 4, 28, 10, 1);
        LocalDateTime respondedAt = LocalDateTime.of(2026, 4, 28, 10, 1, 5);
        NotificationSendTimeoutException exception = new NotificationSendTimeoutException("timeout");

        // given: timeout으로 결과를 확정하지 못한 PROCESSING dispatch가 있다
        when(notificationDispatch.getId()).thenReturn(dispatchId);
        when(notificationDispatch.getStatus()).thenReturn(NotificationDispatchStatus.PROCESSING);
        when(notificationDispatch.getRetryCount()).thenReturn(0);
        when(notificationAttemptRepository.countByNotificationDispatchId(dispatchId)).thenReturn(0L);
        when(timeProvider.now()).thenReturn(respondedAt);
        when(notificationDispatchCommandPort.applyStateChange(
                dispatchId,
                NotificationDispatchStatus.PROCESSING,
                NotificationDispatchStatus.UNKNOWN,
                0,
                null,
                "timeout",
                null,
                respondedAt
        )).thenReturn(true);

        // when: timeout 결과를 후처리하면
        notificationDispatchOutcomeProcessor.handleTimeout(notificationDispatch, requestedAt, exception);

        ArgumentCaptor<NotificationAttempt> attemptCaptor = ArgumentCaptor.forClass(NotificationAttempt.class);

        // then: TIMEOUT attempt를 저장하고 UNKNOWN 상태 변경을 수행한다
        verify(notificationAttemptRepository).save(attemptCaptor.capture());
        verify(notificationDispatchCommandPort).applyStateChange(
                dispatchId,
                NotificationDispatchStatus.PROCESSING,
                NotificationDispatchStatus.UNKNOWN,
                0,
                null,
                "timeout",
                null,
                respondedAt
        );

        NotificationAttempt savedAttempt = attemptCaptor.getValue();
        assertThat(savedAttempt.getAttemptNo()).isEqualTo(1);
        assertThat(savedAttempt.getResultStatus()).isEqualTo(NotificationAttemptResultStatus.TIMEOUT);
        assertThat(savedAttempt.getFailureMessage()).isEqualTo("timeout");
        assertThat(savedAttempt.getRequestedAt()).isEqualTo(requestedAt);
        assertThat(savedAttempt.getRespondedAt()).isEqualTo(respondedAt);
    }

    @Test
    @DisplayName("재시도 가능한 실패 처리 시 retry 정책에 따라 RETRY_WAIT 상태를 반영한다")
    void handlesRetryableFailureWithRetryWait() {
        UUID dispatchId = UUID.randomUUID();
        LocalDateTime requestedAt = LocalDateTime.of(2026, 4, 28, 10, 2);
        LocalDateTime respondedAt = LocalDateTime.of(2026, 4, 28, 10, 2, 4);
        LocalDateTime nextRetryAt = LocalDateTime.of(2026, 4, 28, 10, 7, 4);
        IllegalStateException exception = new IllegalStateException("send failed");

        // given: 재시도 여지가 남아 있는 PROCESSING dispatch가 있다
        when(notificationDispatch.getId()).thenReturn(dispatchId);
        when(notificationDispatch.getStatus()).thenReturn(NotificationDispatchStatus.PROCESSING);
        when(notificationDispatch.getRetryCount()).thenReturn(1);
        when(notificationAttemptRepository.countByNotificationDispatchId(dispatchId)).thenReturn(1L);
        when(timeProvider.now()).thenReturn(respondedAt);
        when(retryPolicy.nextRetryAt(2, respondedAt)).thenReturn(Optional.of(nextRetryAt));
        when(notificationDispatchCommandPort.applyStateChange(
                dispatchId,
                NotificationDispatchStatus.PROCESSING,
                NotificationDispatchStatus.RETRY_WAIT,
                2,
                nextRetryAt,
                "send failed",
                null,
                respondedAt
        )).thenReturn(true);

        // when: 일반 실패 결과를 후처리하면
        notificationDispatchOutcomeProcessor.handleRetryableFailure(notificationDispatch, requestedAt, exception);

        ArgumentCaptor<NotificationAttempt> attemptCaptor = ArgumentCaptor.forClass(NotificationAttempt.class);

        // then: FAILURE attempt를 저장하고 RETRY_WAIT 상태 변경을 수행한다
        verify(notificationAttemptRepository).save(attemptCaptor.capture());
        verify(retryPolicy).nextRetryAt(2, respondedAt);
        verify(notificationDispatchCommandPort).applyStateChange(
                dispatchId,
                NotificationDispatchStatus.PROCESSING,
                NotificationDispatchStatus.RETRY_WAIT,
                2,
                nextRetryAt,
                "send failed",
                null,
                respondedAt
        );

        NotificationAttempt savedAttempt = attemptCaptor.getValue();
        assertThat(savedAttempt.getAttemptNo()).isEqualTo(2);
        assertThat(savedAttempt.getResultStatus()).isEqualTo(NotificationAttemptResultStatus.FAILURE);
        assertThat(savedAttempt.getFailureMessage()).isEqualTo("send failed");
    }

    @Test
    @DisplayName("재시도 가능한 실패 처리 시 재시도 기회가 없으면 FAILED 상태를 반영한다")
    void handlesRetryableFailureWithFailed() {
        UUID dispatchId = UUID.randomUUID();
        LocalDateTime requestedAt = LocalDateTime.of(2026, 4, 28, 10, 3);
        LocalDateTime respondedAt = LocalDateTime.of(2026, 4, 28, 10, 3, 2);
        IllegalStateException exception = new IllegalStateException("final failure");

        // given: 다음 실패에서 재시도 한도를 넘는 PROCESSING dispatch가 있다
        when(notificationDispatch.getId()).thenReturn(dispatchId);
        when(notificationDispatch.getStatus()).thenReturn(NotificationDispatchStatus.PROCESSING);
        when(notificationDispatch.getRetryCount()).thenReturn(3);
        when(notificationAttemptRepository.countByNotificationDispatchId(dispatchId)).thenReturn(3L);
        when(timeProvider.now()).thenReturn(respondedAt);
        when(retryPolicy.nextRetryAt(4, respondedAt)).thenReturn(Optional.empty());
        when(notificationDispatchCommandPort.applyStateChange(
                dispatchId,
                NotificationDispatchStatus.PROCESSING,
                NotificationDispatchStatus.FAILED,
                4,
                null,
                "final failure",
                null,
                respondedAt
        )).thenReturn(true);

        // when: 마지막 실패 결과를 후처리하면
        notificationDispatchOutcomeProcessor.handleRetryableFailure(notificationDispatch, requestedAt, exception);

        ArgumentCaptor<NotificationAttempt> attemptCaptor = ArgumentCaptor.forClass(NotificationAttempt.class);

        // then: FAILURE attempt를 저장하고 FAILED 상태 변경을 수행한다
        verify(notificationAttemptRepository).save(attemptCaptor.capture());
        verify(retryPolicy).nextRetryAt(4, respondedAt);
        verify(notificationDispatchCommandPort).applyStateChange(
                dispatchId,
                NotificationDispatchStatus.PROCESSING,
                NotificationDispatchStatus.FAILED,
                4,
                null,
                "final failure",
                null,
                respondedAt
        );

        NotificationAttempt savedAttempt = attemptCaptor.getValue();
        assertThat(savedAttempt.getAttemptNo()).isEqualTo(4);
        assertThat(savedAttempt.getResultStatus()).isEqualTo(NotificationAttemptResultStatus.FAILURE);
        assertThat(savedAttempt.getFailureMessage()).isEqualTo("final failure");
    }

    @Test
    @DisplayName("재시도 불가능한 실패 처리 시 즉시 FAILED 상태를 반영한다")
    void handlesNonRetryableFailureWithFailed() {
        UUID dispatchId = UUID.randomUUID();
        LocalDateTime requestedAt = LocalDateTime.of(2026, 4, 28, 10, 3);
        LocalDateTime respondedAt = LocalDateTime.of(2026, 4, 28, 10, 3, 2);
        IllegalArgumentException exception = new IllegalArgumentException("permanent failure");

        // given: 즉시 종료해야 하는 PROCESSING dispatch가 있다
        when(notificationDispatch.getId()).thenReturn(dispatchId);
        when(notificationDispatch.getStatus()).thenReturn(NotificationDispatchStatus.PROCESSING);
        when(notificationDispatch.getRetryCount()).thenReturn(0);
        when(notificationAttemptRepository.countByNotificationDispatchId(dispatchId)).thenReturn(0L);
        when(timeProvider.now()).thenReturn(respondedAt);
        when(notificationDispatchCommandPort.applyStateChange(
                dispatchId,
                NotificationDispatchStatus.PROCESSING,
                NotificationDispatchStatus.FAILED,
                1,
                null,
                "permanent failure",
                null,
                respondedAt
        )).thenReturn(true);

        // when: 재시도 불가능한 실패를 후처리하면
        notificationDispatchOutcomeProcessor.handleNonRetryableFailure(notificationDispatch, requestedAt, exception);

        ArgumentCaptor<NotificationAttempt> attemptCaptor = ArgumentCaptor.forClass(NotificationAttempt.class);

        // then: retry 정책 없이 즉시 FAILED 상태를 반영한다
        verify(notificationAttemptRepository).save(attemptCaptor.capture());
        verify(retryPolicy, never()).nextRetryAt(1, respondedAt);
        verify(notificationDispatchCommandPort).applyStateChange(
                dispatchId,
                NotificationDispatchStatus.PROCESSING,
                NotificationDispatchStatus.FAILED,
                1,
                null,
                "permanent failure",
                null,
                respondedAt
        );

        NotificationAttempt savedAttempt = attemptCaptor.getValue();
        assertThat(savedAttempt.getAttemptNo()).isEqualTo(1);
        assertThat(savedAttempt.getResultStatus()).isEqualTo(NotificationAttemptResultStatus.FAILURE);
        assertThat(savedAttempt.getFailureMessage()).isEqualTo("permanent failure");
    }

    @Test
    @DisplayName("sendStartedAt이 있는 stale PROCESSING은 UNKNOWN으로 복구한다")
    void recoversStartedProcessingAsUnknown() {
        UUID dispatchId = UUID.randomUUID();
        LocalDateTime recoveredAt = LocalDateTime.of(2026, 4, 28, 10, 4);
        LocalDateTime sendStartedAt = LocalDateTime.of(2026, 4, 28, 10, 3, 50);

        // given: 외부 발송 시작 시각이 기록된 recovery 대상 PROCESSING dispatch가 있다
        when(notificationDispatch.getId()).thenReturn(dispatchId);
        when(notificationDispatch.getStatus()).thenReturn(NotificationDispatchStatus.PROCESSING);
        when(notificationDispatch.getRetryCount()).thenReturn(2);
        when(notificationDispatch.getSendStartedAt()).thenReturn(sendStartedAt);
        when(notificationDispatchCommandPort.applyStateChange(
                dispatchId,
                NotificationDispatchStatus.PROCESSING,
                NotificationDispatchStatus.UNKNOWN,
                2,
                null,
                "recovered by scheduler",
                null,
                recoveredAt
        )).thenReturn(true);

        // when: recovery가 UNKNOWN 전환을 수행하면
        boolean recovered = notificationDispatchOutcomeProcessor.recoverStaleProcessing(
                notificationDispatch,
                "recovered by scheduler",
                recoveredAt
        );

        // then: attempt는 남기지 않고 UNKNOWN 상태 변경을 수행한다
        assertThat(recovered).isTrue();
        verify(notificationAttemptRepository, never()).save(any(NotificationAttempt.class));
        verify(notificationAttemptRepository, never()).countByNotificationDispatchId(dispatchId);
        verify(notificationDispatchCommandPort).applyStateChange(
                dispatchId,
                NotificationDispatchStatus.PROCESSING,
                NotificationDispatchStatus.UNKNOWN,
                2,
                null,
                "recovered by scheduler",
                null,
                recoveredAt
        );
    }

    @Test
    @DisplayName("sendStartedAt이 없는 stale PROCESSING은 PENDING으로 재큐잉한다")
    void requeuesClaimOnlyProcessingAsPending() {
        UUID dispatchId = UUID.randomUUID();
        LocalDateTime recoveredAt = LocalDateTime.of(2026, 4, 28, 10, 5);

        // given: 선점만 완료되고 외부 발송은 시작하지 못한 PROCESSING dispatch가 있다
        when(notificationDispatch.getId()).thenReturn(dispatchId);
        when(notificationDispatch.getStatus()).thenReturn(NotificationDispatchStatus.PROCESSING);
        when(notificationDispatch.getRetryCount()).thenReturn(0);
        when(notificationDispatch.getSendStartedAt()).thenReturn(null);
        when(notificationDispatchCommandPort.applyStateChange(
                dispatchId,
                NotificationDispatchStatus.PROCESSING,
                NotificationDispatchStatus.PENDING,
                0,
                null,
                null,
                null,
                recoveredAt
        )).thenReturn(true);

        // when: recovery가 stale PROCESSING을 회수하면
        boolean recovered = notificationDispatchOutcomeProcessor.recoverStaleProcessing(
                notificationDispatch,
                "recovered by scheduler",
                recoveredAt
        );

        // then: UNKNOWN 격리 대신 PENDING으로 되돌린다
        assertThat(recovered).isTrue();
        verify(notificationAttemptRepository, never()).save(any(NotificationAttempt.class));
        verify(notificationAttemptRepository, never()).countByNotificationDispatchId(dispatchId);
        verify(notificationDispatchCommandPort).applyStateChange(
                dispatchId,
                NotificationDispatchStatus.PROCESSING,
                NotificationDispatchStatus.PENDING,
                0,
                null,
                null,
                null,
                recoveredAt
        );
    }
}

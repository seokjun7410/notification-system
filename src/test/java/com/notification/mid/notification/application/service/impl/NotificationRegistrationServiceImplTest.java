package com.notification.mid.notification.application.service.impl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import com.notification.mid.common.exception.IdempotencyPayloadMismatchException;
import com.notification.mid.common.time.TimeProvider;
import com.notification.mid.notification.application.port.out.NotificationDispatchCommandPort;
import com.notification.mid.notification.application.port.out.NotificationDispatchQueryPort;
import com.notification.mid.notification.application.port.out.NotificationRepositoryPort;
import com.notification.mid.notification.domain.dispatch.NotificationChannel;
import com.notification.mid.notification.domain.notification.Notification;
import com.notification.mid.notification.domain.notification.NotificationType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class NotificationRegistrationServiceImplTest {

    @Mock
    private NotificationRepositoryPort notificationRepository;

    @Mock
    private NotificationDispatchQueryPort notificationDispatchQueryPort;

    @Mock
    private NotificationDispatchCommandPort notificationDispatchCommandPort;

    @Mock
    private TimeProvider timeProvider;

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private NotificationRegistrationServiceImpl notificationRegistrationService;

    @Test
    @DisplayName("notification 저장 unique 충돌 fallback에서도 payload가 다르면 IDEMPOTENCY_PAYLOAD_MISMATCH를 반환한다")
    void revalidatesPayloadWhenCreateFallbackLoadsExistingNotification() {
        LocalDateTime now = LocalDateTime.of(2026, 4, 28, 10, 0);
        Notification existingNotification = Notification.create(
                "event-1",
                "user-1",
                NotificationType.ORDER,
                "기존 제목",
                "기존 본문",
                now
        );

        // given: 신규 저장은 unique 충돌로 실패하고, fallback 조회에서는 다른 payload의 기존 notification이 발견된다
        when(timeProvider.now()).thenReturn(now);
        when(notificationRepository.findByEventIdAndRecipientIdAndType("event-1", "user-1", NotificationType.ORDER))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existingNotification));
        when(notificationRepository.saveAndFlush(org.mockito.ArgumentMatchers.any(Notification.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        // when & then: fallback 조회 후에도 payload를 다시 검증해 mismatch 예외를 던진다
        assertThatThrownBy(() -> notificationRegistrationService.register(
                "event-1",
                "user-1",
                NotificationType.ORDER,
                NotificationChannel.EMAIL,
                "새 제목",
                "새 본문"
        )).isInstanceOf(IdempotencyPayloadMismatchException.class);

        verify(entityManager).clear();
        verify(notificationDispatchQueryPort, never()).findByNotificationIdAndChannel(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
        verify(notificationDispatchCommandPort, never()).saveAndFlush(
                org.mockito.ArgumentMatchers.any()
        );
    }
}

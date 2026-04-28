package com.notification.mid.notification.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import com.notification.mid.notification.application.port.in.NotificationRecoveryService;
import com.notification.mid.notification.domain.dispatch.NotificationChannel;
import com.notification.mid.notification.domain.dispatch.NotificationDispatch;
import com.notification.mid.notification.domain.dispatch.NotificationDispatchStatus;
import com.notification.mid.notification.domain.notification.Notification;
import com.notification.mid.notification.domain.notification.NotificationType;
import com.notification.mid.notification.infrastructure.repository.JpaNotificationAttemptRepository;
import com.notification.mid.notification.infrastructure.repository.JpaNotificationDispatchRepository;
import com.notification.mid.notification.infrastructure.repository.JpaNotificationRepository;
import com.notification.mid.notification.support.fixture.NotificationDispatchFixture;
import com.notification.mid.notification.support.fixture.NotificationFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class NotificationRecoveryServiceIntegrationTest {

    @Autowired
    private NotificationRecoveryService notificationRecoveryService;

    @Autowired
    private JpaNotificationRepository notificationRepository;

    @Autowired
    private JpaNotificationDispatchRepository notificationDispatchRepository;

    @Autowired
    private JpaNotificationAttemptRepository notificationAttemptRepository;

    @BeforeEach
    void setUp() {
        notificationAttemptRepository.deleteAll();
        notificationDispatchRepository.deleteAll();
        notificationRepository.deleteAll();
    }

    @Test
    @DisplayName("외부 발송을 시작한 오래된 PROCESSING dispatch는 UNKNOWN으로 복구한다")
    void recoversStartedStaleProcessingDispatchAsUnknown() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 4, 26, 9, 0);
        LocalDateTime processingAt = LocalDateTime.of(2026, 4, 26, 9, 1);
        LocalDateTime sendStartedAt = LocalDateTime.of(2026, 4, 26, 9, 2);

        // given: 외부 발송을 시작한 뒤 오래된 PROCESSING 상태의 dispatch가 있다
        Notification notification = notificationRepository.save(NotificationFixture.createNotification(
                "event-1",
                "user-1",
                NotificationType.ORDER,
                "title",
                "content",
                createdAt
        ));
        NotificationDispatch notificationDispatch = NotificationDispatchFixture.createProcessingStartedDispatch(
                notification,
                NotificationChannel.EMAIL,
                createdAt,
                processingAt,
                sendStartedAt
        );
        NotificationDispatch savedDispatch = notificationDispatchRepository.save(notificationDispatch);

        // when: 복구 로직을 수행하면
        int recoveredCount = notificationRecoveryService.recoverStaleProcessingDispatches(10);

        NotificationDispatch foundDispatch = notificationDispatchRepository.findById(savedDispatch.getId()).orElseThrow();

        // then: 결과를 확정할 수 없어 UNKNOWN 상태로 복구된다
        assertThat(recoveredCount).isEqualTo(1);
        assertThat(foundDispatch.getStatus()).isEqualTo(NotificationDispatchStatus.UNKNOWN);
        assertThat(foundDispatch.getLastError()).isEqualTo("오래된 PROCESSING 상태를 UNKNOWN으로 전환했습니다.");
        assertThat(foundDispatch.getNextRetryAt()).isNull();
    }

    @Test
    @DisplayName("아직 외부 발송을 시작하지 않은 오래된 PROCESSING dispatch는 PENDING으로 되돌린다")
    void requeuesStaleClaimedProcessingDispatchToPending() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 4, 26, 9, 0);
        LocalDateTime processingAt = LocalDateTime.of(2026, 4, 26, 9, 1);

        // given: 선점만 완료되고 실제 발송은 시작하지 못한 오래된 PROCESSING dispatch가 있다
        Notification notification = notificationRepository.save(NotificationFixture.createNotification(
                "event-claim-only",
                "user-1",
                NotificationType.ORDER,
                "title",
                "content",
                createdAt
        ));
        NotificationDispatch savedDispatch = notificationDispatchRepository.save(
                NotificationDispatchFixture.createProcessingDispatch(
                        notification,
                        NotificationChannel.EMAIL,
                        createdAt,
                        processingAt
                )
        );

        // when: 복구 로직을 수행하면
        int recoveredCount = notificationRecoveryService.recoverStaleProcessingDispatches(10);

        NotificationDispatch foundDispatch = notificationDispatchRepository.findById(savedDispatch.getId()).orElseThrow();

        // then: 자동 재처리 대상이 되도록 PENDING으로 되돌린다
        assertThat(recoveredCount).isEqualTo(1);
        assertThat(foundDispatch.getStatus()).isEqualTo(NotificationDispatchStatus.PENDING);
        assertThat(foundDispatch.getLastError()).isNull();
        assertThat(foundDispatch.getSendStartedAt()).isNull();
    }

    @Test
    @DisplayName("아직 오래되지 않은 PROCESSING dispatch는 복구하지 않는다")
    void doesNotRecoverRecentProcessingDispatch() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime createdAt = now.minusMinutes(4);
        LocalDateTime processingAt = now.minusMinutes(2);

        // given: recovery 기준 5분을 넘지 않은 PROCESSING dispatch가 있다
        Notification notification = notificationRepository.save(NotificationFixture.createNotification(
                "event-recent-processing",
                "user-1",
                NotificationType.ORDER,
                "title",
                "content",
                createdAt
        ));
        NotificationDispatch savedDispatch = notificationDispatchRepository.save(
                NotificationDispatchFixture.createProcessingDispatch(
                        notification,
                        NotificationChannel.EMAIL,
                        createdAt,
                        processingAt
                )
        );

        // when: 복구 로직을 수행하면
        int recoveredCount = notificationRecoveryService.recoverStaleProcessingDispatches(10);

        NotificationDispatch foundDispatch = notificationDispatchRepository.findById(savedDispatch.getId()).orElseThrow();

        // then: 아직 오래되지 않았으므로 PROCESSING 상태를 유지한다
        assertThat(recoveredCount).isZero();
        assertThat(foundDispatch.getStatus()).isEqualTo(NotificationDispatchStatus.PROCESSING);
        assertThat(foundDispatch.getLastError()).isNull();
    }
}

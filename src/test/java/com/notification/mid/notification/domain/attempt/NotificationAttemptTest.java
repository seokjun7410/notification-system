package com.notification.mid.notification.domain.attempt;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import com.notification.mid.notification.domain.dispatch.NotificationChannel;
import com.notification.mid.notification.domain.dispatch.NotificationDispatch;
import com.notification.mid.notification.domain.notification.Notification;
import com.notification.mid.notification.support.fixture.NotificationDispatchFixture;
import com.notification.mid.notification.support.fixture.NotificationFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NotificationAttemptTest {

    @Test
    @DisplayName("성공 attempt 생성 시 성공 결과와 요청 시각 메타 정보가 저장된다")
    void createsSuccessAttempt() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 4, 25, 10, 0);
        LocalDateTime requestedAt = createdAt.plusMinutes(1);
        LocalDateTime respondedAt = requestedAt.plusSeconds(2);

        // given: 성공 이력을 남길 dispatch가 있다
        Notification notification = NotificationFixture.createNotification(createdAt);
        NotificationDispatch notificationDispatch = NotificationDispatchFixture.createPendingDispatch(
                notification,
                NotificationChannel.EMAIL,
                createdAt
        );

        // when: 성공 attempt를 생성하면
        NotificationAttempt notificationAttempt = NotificationAttempt.success(
                notificationDispatch,
                1,
                requestedAt,
                respondedAt
        );

        // then: 성공 결과와 요청 시각 기준 메타 정보가 초기화된다
        assertThat(notificationAttempt.getNotificationDispatch()).isEqualTo(notificationDispatch);
        assertThat(notificationAttempt.getAttemptNo()).isEqualTo(1);
        assertThat(notificationAttempt.getResultStatus()).isEqualTo(NotificationAttemptResultStatus.SUCCESS);
        assertThat(notificationAttempt.getFailureMessage()).isNull();
        assertThat(notificationAttempt.getRequestedAt()).isEqualTo(requestedAt);
        assertThat(notificationAttempt.getRespondedAt()).isEqualTo(respondedAt);
        assertThat(notificationAttempt.getCreatedAt()).isEqualTo(requestedAt);
        assertThat(notificationAttempt.getUpdatedAt()).isEqualTo(requestedAt);
    }

    @Test
    @DisplayName("실패 attempt 생성 시 실패 메시지를 함께 저장한다")
    void createsFailureAttempt() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 4, 25, 10, 0);
        LocalDateTime requestedAt = createdAt.plusMinutes(1);
        LocalDateTime respondedAt = requestedAt.plusSeconds(2);

        // given: 실패 이력을 남길 dispatch가 있다
        Notification notification = NotificationFixture.createNotification(createdAt);
        NotificationDispatch notificationDispatch = NotificationDispatchFixture.createPendingDispatch(
                notification,
                NotificationChannel.EMAIL,
                createdAt
        );

        // when: 실패 attempt를 생성하면
        NotificationAttempt notificationAttempt = NotificationAttempt.failure(
                notificationDispatch,
                2,
                "network error",
                requestedAt,
                respondedAt
        );

        // then: 실패 결과와 오류 메시지가 함께 저장된다
        assertThat(notificationAttempt.getAttemptNo()).isEqualTo(2);
        assertThat(notificationAttempt.getResultStatus()).isEqualTo(NotificationAttemptResultStatus.FAILURE);
        assertThat(notificationAttempt.getFailureMessage()).isEqualTo("network error");
    }

    @Test
    @DisplayName("timeout attempt 생성 시 결과를 TIMEOUT으로 기록한다")
    void createsTimeoutAttempt() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 4, 25, 10, 0);
        LocalDateTime requestedAt = createdAt.plusMinutes(1);
        LocalDateTime respondedAt = requestedAt.plusSeconds(5);

        // given: timeout 이력을 남길 dispatch가 있다
        Notification notification = NotificationFixture.createNotification(createdAt);
        NotificationDispatch notificationDispatch = NotificationDispatchFixture.createPendingDispatch(
                notification,
                NotificationChannel.EMAIL,
                createdAt
        );

        // when: timeout attempt를 생성하면
        NotificationAttempt notificationAttempt = NotificationAttempt.timeout(
                notificationDispatch,
                3,
                "timeout",
                requestedAt,
                respondedAt
        );

        // then: 결과 상태는 TIMEOUT이고 실패 메시지가 보존된다
        assertThat(notificationAttempt.getAttemptNo()).isEqualTo(3);
        assertThat(notificationAttempt.getResultStatus()).isEqualTo(NotificationAttemptResultStatus.TIMEOUT);
        assertThat(notificationAttempt.getFailureMessage()).isEqualTo("timeout");
        assertThat(notificationAttempt.getRespondedAt()).isEqualTo(respondedAt);
    }
}

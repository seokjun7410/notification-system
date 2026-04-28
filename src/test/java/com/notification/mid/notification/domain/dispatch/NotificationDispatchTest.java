package com.notification.mid.notification.domain.dispatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import com.notification.mid.notification.domain.notification.Notification;
import com.notification.mid.notification.support.fixture.NotificationDispatchFixture;
import com.notification.mid.notification.support.fixture.NotificationFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NotificationDispatchTest {

    @Test
    @DisplayName("발송 정보 생성 시 기본 상태는 PENDING이다")
    void createsPendingDispatch() {
        // given: 발송 대상을 만들 알림 원본이 있다
        LocalDateTime now = LocalDateTime.of(2026, 4, 25, 10, 0);
        Notification notification = NotificationFixture.createNotification(now);

        // when: EMAIL 발송 정보를 생성하면
        NotificationDispatch notificationDispatch = NotificationDispatchFixture.createPendingDispatch(
                notification,
                NotificationChannel.EMAIL,
                now
        );

        // then: 기본 상태와 읽음 상태가 초기화된다
        assertThat(notificationDispatch.getStatus()).isEqualTo(NotificationDispatchStatus.PENDING);
        assertThat(notificationDispatch.getRetryCount()).isZero();
        assertThat(notificationDispatch.getReadStatus()).isEqualTo(NotificationReadStatus.UNKNOWN);
        assertThat(notificationDispatch.getDeletedAt()).isNull();
        assertThat(notificationDispatch.isDeleted()).isFalse();
    }

    @Test
    @DisplayName("PROCESSING 상태에서 SUCCESS로 전이할 수 있다")
    void marksSuccessFromProcessing() {
        LocalDateTime now = LocalDateTime.of(2026, 4, 25, 10, 0);
        Notification notification = NotificationFixture.createNotification(now);

        // given: 처리 가능한 발송 정보가 있다
        NotificationDispatch notificationDispatch = NotificationDispatchFixture.createPendingDispatch(
                notification,
                NotificationChannel.EMAIL,
                now
        );

        // when: PROCESSING을 거쳐 SUCCESS로 전이하면
        notificationDispatch.markProcessing(now.plusSeconds(1));
        notificationDispatch.markSuccess(now.plusSeconds(2));

        // then: SUCCESS 상태와 수정 시각이 반영된다
        assertThat(notificationDispatch.getStatus()).isEqualTo(NotificationDispatchStatus.SUCCESS);
        assertThat(notificationDispatch.getUpdatedAt()).isEqualTo(now.plusSeconds(2));
    }

    @Test
    @DisplayName("IN_APP 채널은 읽음 상태를 계산할 수 있다")
    void calculatesReadStatusForInApp() {
        LocalDateTime now = LocalDateTime.of(2026, 4, 25, 10, 0);
        Notification notification = NotificationFixture.createNotification(now);

        // given: 읽지 않은 IN_APP 발송 정보가 있다
        NotificationDispatch notificationDispatch = NotificationDispatchFixture.createPendingDispatch(
                notification,
                NotificationChannel.IN_APP,
                now
        );

        // when: IN_APP 발송을 읽음 처리하면
        notificationDispatch.markRead(now.plusMinutes(1));

        // then: 읽음 상태가 READ로 계산된다
        assertThat(notificationDispatch.getReadStatus()).isEqualTo(NotificationReadStatus.READ);
    }

    @Test
    @DisplayName("종결 상태에서 PROCESSING으로 되돌릴 수 없다")
    void rejectsInvalidTransition() {
        LocalDateTime now = LocalDateTime.of(2026, 4, 25, 10, 0);
        Notification notification = NotificationFixture.createNotification(now);
        NotificationDispatch notificationDispatch = NotificationDispatchFixture.createPendingDispatch(
                notification,
                NotificationChannel.EMAIL,
                now
        );

        // given: 이미 SUCCESS가 된 발송 정보가 있다
        notificationDispatch.markProcessing(now.plusSeconds(1));
        notificationDispatch.markSuccess(now.plusSeconds(2));

        // when: 종결 상태를 다시 PROCESSING으로 바꾸려 하면

        // then: 허용되지 않은 상태 전이 예외가 발생한다
        assertThatThrownBy(() -> notificationDispatch.markProcessing(now.plusSeconds(3)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("허용되지 않은 알림 발송 상태 전이입니다.");
    }
}

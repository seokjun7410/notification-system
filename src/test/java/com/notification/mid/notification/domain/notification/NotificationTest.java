package com.notification.mid.notification.domain.notification;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.LocalDateTime;

import com.notification.mid.notification.support.fixture.NotificationFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NotificationTest {

    @Test
    @DisplayName("알림 원본 생성 시 기본 메타 정보가 초기화된다")
    void createsNotification() {
        LocalDateTime now = LocalDateTime.of(2026, 4, 25, 10, 0);

        // given: 생성 시각이 고정된 알림 원본 입력이 있다
        Notification notification = NotificationFixture.createNotification(now);

        // when: 알림 원본을 생성하면

        // then: 본문 필드와 생성 메타 정보가 함께 초기화된다
        assertThat(notification.getEventId()).isEqualTo("event-1");
        assertThat(notification.getRecipientId()).isEqualTo("user-1");
        assertThat(notification.getType()).isEqualTo(NotificationType.ORDER);
        assertThat(notification.getTitle()).isEqualTo("title");
        assertThat(notification.getContent()).isEqualTo("content");
        assertThat(notification.getCreatedAt()).isEqualTo(now);
        assertThat(notification.getUpdatedAt()).isEqualTo(now);
        assertThat(notification.getDeletedAt()).isNull();
        assertThat(notification.isDeleted()).isFalse();
    }

    @Test
    @DisplayName("알림 원본은 title과 content가 같은지 비교할 수 있다")
    void comparesPayload() {
        LocalDateTime now = LocalDateTime.of(2026, 4, 25, 10, 0);

        // given: 기존 payload를 가진 알림 원본이 있다
        Notification notification = NotificationFixture.createNotification(now);

        // when: title과 content 일치 여부를 비교하면
        boolean samePayload = notification.hasSamePayload("title", "content");
        boolean differentTitle = notification.hasSamePayload("other-title", "content");
        boolean differentContent = notification.hasSamePayload("title", "other-content");

        // then: 완전히 같은 payload만 true가 된다
        assertThat(samePayload).isTrue();
        assertThat(differentTitle).isFalse();
        assertThat(differentContent).isFalse();
    }
}

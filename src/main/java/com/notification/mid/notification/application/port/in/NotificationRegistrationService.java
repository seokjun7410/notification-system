package com.notification.mid.notification.application.port.in;

import com.notification.mid.notification.application.dto.response.NotificationCreateRes;
import com.notification.mid.notification.domain.dispatch.NotificationChannel;
import com.notification.mid.notification.domain.notification.NotificationType;

public interface NotificationRegistrationService {

    /**
     * 신규 알림 요청을 등록한다.
     * 같은 eventId, recipientId, type의 알림 원본은 재사용하고,
     * 같은 알림 원본에 같은 channel dispatch가 이미 존재하면 예외를 발생시킨다.
     */
    NotificationCreateRes register(
            String eventId,
            String recipientId,
            NotificationType type,
            NotificationChannel channel,
            String title,
            String content
    );
}

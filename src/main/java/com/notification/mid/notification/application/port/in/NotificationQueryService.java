package com.notification.mid.notification.application.port.in;

import com.notification.mid.notification.application.dto.response.NotificationDetailRes;
import com.notification.mid.notification.application.dto.response.NotificationPageRes;
import com.notification.mid.notification.application.dto.response.UserNotificationPageRes;
import com.notification.mid.notification.domain.dispatch.NotificationChannel;
import com.notification.mid.notification.domain.dispatch.NotificationReadStatus;

import java.util.UUID;

public interface NotificationQueryService {

    /**
     * 알림 원본 ID로 현재 상태를 조회한다.
     */
    NotificationDetailRes getNotification(UUID notificationId);

    /**
     * 관리자용 수신자 알림 목록을 조회한다.
     */
    NotificationPageRes searchAdmin(
            String recipientId,
            NotificationChannel channel,
            NotificationReadStatus readStatus,
            int size,
            String cursor
    );

    /**
     * 사용자용 IN_APP 발송 완료 알림 목록을 조회한다.
     */
    UserNotificationPageRes searchUserInApp(
            String recipientId,
            NotificationReadStatus readStatus,
            int size,
            String cursor
    );
}

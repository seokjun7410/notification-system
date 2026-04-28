package com.notification.mid.notification.application.port.out;

import com.notification.mid.notification.application.exception.NotificationSendTimeoutException;
import com.notification.mid.notification.application.exception.NonRetryableNotificationSendException;
import com.notification.mid.notification.application.exception.RetryableNotificationSendException;
import com.notification.mid.notification.domain.dispatch.NotificationDispatch;
import com.notification.mid.notification.domain.notification.Notification;

public interface NotificationSender {

    /**
     * 하나의 notification을 특정 channel dispatch로 실제 발송한다.
     * 구현체는 성공 시 정상 반환하고,
     * 재시도 가능한 실패는 {@link RetryableNotificationSendException},
     * 즉시 종료해야 할 실패는 {@link NonRetryableNotificationSendException},
     * timeout은 {@link NotificationSendTimeoutException} 으로 표현한다.
     */
    void send(Notification notification, NotificationDispatch notificationDispatch);
}

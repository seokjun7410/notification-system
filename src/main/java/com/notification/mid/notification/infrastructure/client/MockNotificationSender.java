package com.notification.mid.notification.infrastructure.client;

import com.notification.mid.notification.application.exception.NotificationSendTimeoutException;
import com.notification.mid.notification.application.exception.NonRetryableNotificationSendException;
import com.notification.mid.notification.application.exception.RetryableNotificationSendException;
import com.notification.mid.notification.application.port.out.NotificationSender;
import com.notification.mid.notification.domain.dispatch.NotificationDispatch;
import com.notification.mid.notification.domain.notification.Notification;
import org.springframework.stereotype.Component;

@Component
public class MockNotificationSender implements NotificationSender {

    private static final String FAIL_PREFIX = "fail-";
    private static final String PERMANENT_FAIL_PREFIX = "permanent-fail-";
    private static final String TIMEOUT_PREFIX = "timeout-";

    @Override
    public void send(Notification notification, NotificationDispatch notificationDispatch) {
        if (notification.getEventId().startsWith(TIMEOUT_PREFIX)) {
            throw new NotificationSendTimeoutException("이메일 발송 응답 시간이 초과되었습니다.");
        }

        if (notification.getEventId().startsWith(PERMANENT_FAIL_PREFIX)) {
            throw new NonRetryableNotificationSendException("Mock dispatch permanent failure");
        }

        if (notification.getEventId().startsWith(FAIL_PREFIX)) {
            throw new RetryableNotificationSendException("Mock dispatch failure");
        }
    }
}

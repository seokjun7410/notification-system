package com.notification.mid.notification.support.fixture;

import com.notification.mid.notification.domain.dispatch.NotificationChannel;
import com.notification.mid.notification.domain.notification.NotificationType;
import com.notification.mid.notification.presentation.dto.request.NotificationCreateReq;

public final class NotificationCreateReqFixture {

    private NotificationCreateReqFixture() {
    }

    public static NotificationCreateReq createRequest() {
        return new NotificationCreateReq(
                "order-paid-10001",
                "user-1",
                NotificationType.ORDER,
                NotificationChannel.EMAIL,
                "주문 결제가 완료되었습니다",
                "주문번호 10001의 결제가 완료되었습니다."
        );
    }

    public static NotificationCreateReq createRequest(
            String eventId,
            String recipientId,
            NotificationChannel channel
    ) {
        return createRequest(
                eventId,
                recipientId,
                channel,
                "주문 결제가 완료되었습니다",
                "주문번호 10001의 결제가 완료되었습니다."
        );
    }

    public static NotificationCreateReq createRequest(
            String eventId,
            String recipientId,
            NotificationChannel channel,
            String title,
            String content
    ) {
        return new NotificationCreateReq(
                eventId,
                recipientId,
                NotificationType.ORDER,
                channel,
                title,
                content
        );
    }
}

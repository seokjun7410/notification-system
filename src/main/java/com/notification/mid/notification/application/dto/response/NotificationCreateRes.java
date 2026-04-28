package com.notification.mid.notification.application.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

import com.notification.mid.notification.domain.dispatch.NotificationChannel;
import com.notification.mid.notification.domain.dispatch.NotificationDispatch;
import com.notification.mid.notification.domain.dispatch.NotificationDispatchStatus;
import com.notification.mid.notification.domain.notification.Notification;
import com.notification.mid.notification.domain.notification.NotificationType;

public record NotificationCreateRes(
        UUID notificationId,
        UUID dispatchId,
        String eventId,
        String recipientId,
        NotificationType type,
        NotificationChannel channel,
        NotificationDispatchStatus status,
        int retryCount,
        LocalDateTime nextRetryAt,
        LocalDateTime createdAt
) {

    public static NotificationCreateRes from(Notification notification, NotificationDispatch notificationDispatch) {
        return new NotificationCreateRes(
                notification.getId(),
                notificationDispatch.getId(),
                notification.getEventId(),
                notification.getRecipientId(),
                notification.getType(),
                notificationDispatch.getChannel(),
                notificationDispatch.getStatus(),
                notificationDispatch.getRetryCount(),
                notificationDispatch.getNextRetryAt(),
                notificationDispatch.getCreatedAt()
        );
    }
}

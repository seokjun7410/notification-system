package com.notification.mid.notification.application.dto;

import java.util.UUID;

import com.notification.mid.notification.domain.dispatch.NotificationChannel;
import com.notification.mid.notification.domain.dispatch.NotificationDispatch;
import com.notification.mid.notification.domain.notification.Notification;
import com.notification.mid.notification.domain.notification.NotificationType;

public record NotificationDispatchTargetDto(
        UUID dispatchId,
        UUID notificationId,
        String eventId,
        String recipientId,
        NotificationType type,
        NotificationChannel channel,
        String title,
        String content
) {

    public static NotificationDispatchTargetDto from(NotificationDispatch notificationDispatch) {
        Notification notification = notificationDispatch.getNotification();

        return new NotificationDispatchTargetDto(
                notificationDispatch.getId(),
                notification.getId(),
                notification.getEventId(),
                notification.getRecipientId(),
                notification.getType(),
                notificationDispatch.getChannel(),
                notification.getTitle(),
                notification.getContent()
        );
    }
}

package com.notification.mid.notification.application.dto.response;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.notification.mid.notification.domain.notification.Notification;
import com.notification.mid.notification.domain.notification.NotificationType;

public record NotificationDetailRes(
        UUID notificationId,
        String eventId,
        String recipientId,
        NotificationType type,
        String title,
        String content,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<NotificationDispatchDetailRes> dispatches
) {

    public static NotificationDetailRes from(
            Notification notification,
            List<NotificationDispatchDetailRes> dispatches
    ) {
        return new NotificationDetailRes(
                notification.getId(),
                notification.getEventId(),
                notification.getRecipientId(),
                notification.getType(),
                notification.getTitle(),
                notification.getContent(),
                notification.getCreatedAt(),
                notification.getUpdatedAt(),
                dispatches
        );
    }
}

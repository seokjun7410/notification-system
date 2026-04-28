package com.notification.mid.notification.application.dto.response;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.notification.mid.notification.domain.notification.Notification;
import com.notification.mid.notification.domain.notification.NotificationType;

public record NotificationSummaryRes(
        UUID notificationId,
        String eventId,
        NotificationType type,
        String title,
        LocalDateTime createdAt,
        List<NotificationDispatchSummaryRes> dispatches
) {

    public static NotificationSummaryRes from(
            Notification notification,
            List<NotificationDispatchSummaryRes> dispatches
    ) {
        return new NotificationSummaryRes(
                notification.getId(),
                notification.getEventId(),
                notification.getType(),
                notification.getTitle(),
                notification.getCreatedAt(),
                dispatches
        );
    }
}

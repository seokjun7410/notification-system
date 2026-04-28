package com.notification.mid.notification.application.dto.response;

import com.notification.mid.notification.domain.notification.Notification;
import com.notification.mid.notification.domain.notification.NotificationType;

import java.time.LocalDateTime;
import java.util.UUID;

public record UserNotificationSummaryRes(
        UUID notificationId,
        String eventId,
        NotificationType type,
        String title,
        LocalDateTime createdAt
) {

    public static UserNotificationSummaryRes from(Notification notification) {
        return new UserNotificationSummaryRes(
                notification.getId(),
                notification.getEventId(),
                notification.getType(),
                notification.getTitle(),
                notification.getCreatedAt()
        );
    }
}

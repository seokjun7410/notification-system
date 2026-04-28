package com.notification.mid.notification.application.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

import com.notification.mid.notification.domain.dispatch.NotificationChannel;
import com.notification.mid.notification.domain.dispatch.NotificationDispatch;
import com.notification.mid.notification.domain.dispatch.NotificationDispatchStatus;
import com.notification.mid.notification.domain.dispatch.NotificationReadStatus;

public record NotificationDispatchSummaryRes(
        UUID dispatchId,
        NotificationChannel channel,
        NotificationDispatchStatus status,
        int retryCount,
        LocalDateTime nextRetryAt,
        String lastError,
        NotificationReadStatus readStatus,
        LocalDateTime readAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static NotificationDispatchSummaryRes from(NotificationDispatch notificationDispatch) {
        return new NotificationDispatchSummaryRes(
                notificationDispatch.getId(),
                notificationDispatch.getChannel(),
                notificationDispatch.getStatus(),
                notificationDispatch.getRetryCount(),
                notificationDispatch.getNextRetryAt(),
                notificationDispatch.getLastError(),
                notificationDispatch.getReadStatus(),
                notificationDispatch.getReadAt(),
                notificationDispatch.getCreatedAt(),
                notificationDispatch.getUpdatedAt()
        );
    }
}

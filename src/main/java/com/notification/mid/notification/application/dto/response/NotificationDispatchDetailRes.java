package com.notification.mid.notification.application.dto.response;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.notification.mid.notification.domain.attempt.NotificationAttempt;
import com.notification.mid.notification.domain.dispatch.NotificationChannel;
import com.notification.mid.notification.domain.dispatch.NotificationDispatch;
import com.notification.mid.notification.domain.dispatch.NotificationDispatchStatus;
import com.notification.mid.notification.domain.dispatch.NotificationReadStatus;

public record NotificationDispatchDetailRes(
        UUID dispatchId,
        NotificationChannel channel,
        NotificationDispatchStatus status,
        int retryCount,
        LocalDateTime nextRetryAt,
        String lastError,
        NotificationReadStatus readStatus,
        LocalDateTime readAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<NotificationAttemptRes> attempts
) {

    public static NotificationDispatchDetailRes from(
            NotificationDispatch notificationDispatch,
            List<NotificationAttempt> attempts
    ) {
        return new NotificationDispatchDetailRes(
                notificationDispatch.getId(),
                notificationDispatch.getChannel(),
                notificationDispatch.getStatus(),
                notificationDispatch.getRetryCount(),
                notificationDispatch.getNextRetryAt(),
                notificationDispatch.getLastError(),
                notificationDispatch.getReadStatus(),
                notificationDispatch.getReadAt(),
                notificationDispatch.getCreatedAt(),
                notificationDispatch.getUpdatedAt(),
                attempts.stream()
                        .map(NotificationAttemptRes::from)
                        .toList()
        );
    }
}

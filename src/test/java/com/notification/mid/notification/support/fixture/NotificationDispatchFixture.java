package com.notification.mid.notification.support.fixture;

import java.time.LocalDateTime;

import com.notification.mid.notification.domain.dispatch.NotificationChannel;
import com.notification.mid.notification.domain.dispatch.NotificationDispatch;
import com.notification.mid.notification.domain.notification.Notification;

public final class NotificationDispatchFixture {

    private NotificationDispatchFixture() {
    }

    public static NotificationDispatch createPendingDispatch(
            Notification notification,
            NotificationChannel channel,
            LocalDateTime createdAt
    ) {
        return NotificationDispatch.create(notification, channel, createdAt);
    }

    public static NotificationDispatch createProcessingDispatch(
            Notification notification,
            NotificationChannel channel,
            LocalDateTime createdAt,
            LocalDateTime processingAt
    ) {
        NotificationDispatch notificationDispatch = createPendingDispatch(notification, channel, createdAt);
        notificationDispatch.markProcessing(processingAt);
        return notificationDispatch;
    }

    public static NotificationDispatch createProcessingStartedDispatch(
            Notification notification,
            NotificationChannel channel,
            LocalDateTime createdAt,
            LocalDateTime processingAt,
            LocalDateTime sendStartedAt
    ) {
        NotificationDispatch notificationDispatch = createProcessingDispatch(notification, channel, createdAt, processingAt);
        notificationDispatch.markSendStarted(sendStartedAt);
        return notificationDispatch;
    }

    public static NotificationDispatch createRetryWaitDispatch(
            Notification notification,
            NotificationChannel channel,
            LocalDateTime createdAt,
            LocalDateTime processingAt,
            int retryCount,
            LocalDateTime nextRetryAt,
            String lastError
    ) {
        NotificationDispatch notificationDispatch = createProcessingDispatch(notification, channel, createdAt, processingAt);
        notificationDispatch.markRetryWait(retryCount, nextRetryAt, lastError, processingAt);
        return notificationDispatch;
    }

    public static NotificationDispatch createReadInAppDispatch(
            Notification notification,
            LocalDateTime createdAt,
            LocalDateTime readAt
    ) {
        NotificationDispatch notificationDispatch = createPendingDispatch(notification, NotificationChannel.IN_APP, createdAt);
        notificationDispatch.markRead(readAt);
        return notificationDispatch;
    }

    public static NotificationDispatch createSuccessDispatch(
            Notification notification,
            NotificationChannel channel,
            LocalDateTime createdAt,
            LocalDateTime successAt
    ) {
        NotificationDispatch notificationDispatch = createProcessingDispatch(notification, channel, createdAt, createdAt);
        notificationDispatch.markSuccess(successAt);
        return notificationDispatch;
    }

    public static NotificationDispatch createSuccessReadInAppDispatch(
            Notification notification,
            LocalDateTime createdAt,
            LocalDateTime successAt,
            LocalDateTime readAt
    ) {
        NotificationDispatch notificationDispatch = createSuccessDispatch(
                notification,
                NotificationChannel.IN_APP,
                createdAt,
                successAt
        );
        notificationDispatch.markRead(readAt);
        return notificationDispatch;
    }
}

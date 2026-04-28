package com.notification.mid.notification.application.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

import com.notification.mid.notification.domain.attempt.NotificationAttempt;
import com.notification.mid.notification.domain.attempt.NotificationAttemptResultStatus;

public record NotificationAttemptRes(
        UUID attemptId,
        int attemptNo,
        NotificationAttemptResultStatus resultStatus,
        String failureMessage,
        LocalDateTime requestedAt,
        LocalDateTime respondedAt
) {

    public static NotificationAttemptRes from(NotificationAttempt notificationAttempt) {
        return new NotificationAttemptRes(
                notificationAttempt.getId(),
                notificationAttempt.getAttemptNo(),
                notificationAttempt.getResultStatus(),
                notificationAttempt.getFailureMessage(),
                notificationAttempt.getRequestedAt(),
                notificationAttempt.getRespondedAt()
        );
    }
}

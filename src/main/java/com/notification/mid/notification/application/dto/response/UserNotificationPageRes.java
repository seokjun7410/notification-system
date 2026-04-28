package com.notification.mid.notification.application.dto.response;

import java.util.List;

public record UserNotificationPageRes(
        List<UserNotificationSummaryRes> content,
        int size,
        boolean hasNext,
        String nextCursor
) {

    public static UserNotificationPageRes of(
            List<UserNotificationSummaryRes> content,
            int size,
            boolean hasNext,
            String nextCursor
    ) {
        return new UserNotificationPageRes(
                content,
                size,
                hasNext,
                nextCursor
        );
    }
}

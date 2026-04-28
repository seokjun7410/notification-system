package com.notification.mid.notification.application.dto.response;

import java.util.List;

public record NotificationPageRes(
        List<NotificationSummaryRes> content,
        int size,
        boolean hasNext,
        String nextCursor
) {

    public static NotificationPageRes of(
            List<NotificationSummaryRes> content,
            int size,
            boolean hasNext,
            String nextCursor
    ) {
        return new NotificationPageRes(
                content,
                size,
                hasNext,
                nextCursor
        );
    }
}

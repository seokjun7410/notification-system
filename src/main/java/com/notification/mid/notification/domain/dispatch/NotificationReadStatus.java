package com.notification.mid.notification.domain.dispatch;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NotificationReadStatus {

    READ("읽음"),
    UNREAD("안읽음"),
    UNKNOWN("확인 불가");

    private final String description;

    /**
     * 채널별 읽음 의미를 조회용 상태로 변환한다.
     * 읽음 개념이 있는 IN_APP만 READ/UNREAD를 가지며, 나머지 채널은 UNKNOWN으로 본다.
     */
    public static NotificationReadStatus from(NotificationChannel channel, LocalDateTime readAt) {
        if (channel != NotificationChannel.IN_APP) {
            return UNKNOWN;
        }

        return readAt == null ? UNREAD : READ;
    }
}

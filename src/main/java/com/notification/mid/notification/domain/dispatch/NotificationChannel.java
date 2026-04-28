package com.notification.mid.notification.domain.dispatch;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NotificationChannel {

    EMAIL("이메일"),
    IN_APP("인앱 알림");

    private final String description;
}

package com.notification.mid.notification.domain.attempt;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NotificationAttemptResultStatus {

    SUCCESS("발송 성공"),
    FAILURE("발송 실패"),
    TIMEOUT("응답 시간 초과");

    private final String description;
}

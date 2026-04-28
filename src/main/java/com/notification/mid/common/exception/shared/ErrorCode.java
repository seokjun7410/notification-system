package com.notification.mid.common.exception.shared;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "잘못된 요청입니다."),
    DUPLICATE_NOTIFICATION_REQUEST(HttpStatus.CONFLICT, "DUPLICATE_NOTIFICATION_REQUEST", "이미 등록된 알림 요청입니다."),
    IDEMPOTENCY_PAYLOAD_MISMATCH(HttpStatus.CONFLICT, "IDEMPOTENCY_PAYLOAD_MISMATCH", "같은 멱등성 키에 다른 payload를 보낼 수 없습니다."),
    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "NOTIFICATION_NOT_FOUND", "알림을 찾을 수 없습니다."),
    NOTIFICATION_DISPATCH_NOT_FOUND(HttpStatus.NOT_FOUND, "NOTIFICATION_DISPATCH_NOT_FOUND", "알림 발송 정보를 찾을 수 없습니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "서버 내부 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}

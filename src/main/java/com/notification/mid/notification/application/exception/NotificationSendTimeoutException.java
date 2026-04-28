package com.notification.mid.notification.application.exception;

public class NotificationSendTimeoutException extends RuntimeException {

    /**
     * 외부 채널의 응답 지연으로 결과를 확정할 수 없음을 표현한다.
     * 일반 실패와 구분해 dispatch를 UNKNOWN으로 분기하기 위한 예외다.
     */
    public NotificationSendTimeoutException(String message) {
        super(message);
    }
}

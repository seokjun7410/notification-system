package com.notification.mid.notification.domain.dispatch;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NotificationDispatchStatus {

    PENDING("대기"),
    PROCESSING("처리 중"),
    RETRY_WAIT("재시도 대기"),
    SUCCESS("발송 성공"),
    UNKNOWN("결과 알 수 없음"),
    FAILED("최종 실패");

    private final String description;

    /**
     * 현재 상태에서 다음 상태로 전이할 수 있는지 판단한다.
     * 이 규칙은 dispatch의 상태 머신을 구성하며, 종결 상태로 들어간 뒤에는 다시 처리 흐름으로 되돌아가지 않는다.
     */
    public boolean canChangeTo(NotificationDispatchStatus nextStatus) {
        return switch (this) {
            case PENDING, RETRY_WAIT -> nextStatus == PROCESSING;
            case PROCESSING -> nextStatus == PENDING
                    || nextStatus == SUCCESS
                    || nextStatus == RETRY_WAIT
                    || nextStatus == UNKNOWN
                    || nextStatus == FAILED;
            case SUCCESS, UNKNOWN, FAILED -> false;
        };
    }

    public void assertCanChangeTo(NotificationDispatchStatus nextStatus) {
        if (!canChangeTo(nextStatus)) {
            throw new IllegalStateException(
                    "허용되지 않은 알림 발송 상태 전이입니다. currentStatus=" + this + ", nextStatus=" + nextStatus
            );
        }
    }
}

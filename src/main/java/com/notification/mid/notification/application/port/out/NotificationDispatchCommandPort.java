package com.notification.mid.notification.application.port.out;

import java.time.LocalDateTime;
import java.util.UUID;

import com.notification.mid.notification.domain.dispatch.NotificationDispatch;
import com.notification.mid.notification.domain.dispatch.NotificationDispatchStatus;

/**
 * dispatch를 저장하거나 상태를 반영하는 쓰기용 outbound port다.
 * 등록 단계의 생성과 worker/recovery의 상태 반영이 이 포트를 통해 이뤄진다.
 */
public interface NotificationDispatchCommandPort {

    /**
     * 새 dispatch를 저장하고 즉시 flush한다.
     * 등록 직후 channel unique 제약 위반을 빠르게 감지하고 후속 응답 데이터를 확정할 때 사용한다.
     */
    NotificationDispatch saveAndFlush(NotificationDispatch notificationDispatch);

    /**
     * 실제 외부 채널 호출 직전에 발송 시작 시각을 기록한다.
     * recovery가 아직 발송을 시작하지 못한 PROCESSING과 이미 외부 호출에 들어간 PROCESSING을 구분할 수 있게 한다.
     */
    boolean markSendStarted(UUID dispatchId, LocalDateTime sendStartedAt, LocalDateTime updatedAt);

    /**
     * 현재 DB 상태가 expectedCurrentStatus와 일치할 때만 다음 상태로 원자적으로 갱신한다.
     * worker와 recovery가 같은 row를 경합하더라도 마지막 쓰기가 이전 상태를 덮어쓰지 않도록 CAS로 반영한다.
     */
    boolean applyStateChange(
            UUID dispatchId,
            NotificationDispatchStatus expectedCurrentStatus,
            NotificationDispatchStatus nextStatus,
            int retryCount,
            LocalDateTime nextRetryAt,
            String lastError,
            LocalDateTime sendStartedAt,
            LocalDateTime updatedAt
    );
}

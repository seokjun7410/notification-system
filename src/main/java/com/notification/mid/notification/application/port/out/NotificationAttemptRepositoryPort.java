package com.notification.mid.notification.application.port.out;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import com.notification.mid.notification.domain.attempt.NotificationAttempt;

public interface NotificationAttemptRepositoryPort {

    /**
     * 발송 시도 이력을 저장한다.
     * dispatch 최종 상태와 별개로, 성공/실패/timeout의 개별 시도 기록을 append-only로 남긴다.
     */
    NotificationAttempt save(NotificationAttempt notificationAttempt);

    /**
     * 여러 dispatch의 시도 이력을 attempt 순서대로 한 번에 조회한다.
     * 상세 조회 응답에서 dispatch별 attempt 배열을 조립할 때 사용한다.
     */
    List<NotificationAttempt> findByNotificationDispatchIdInOrderByAttemptNoAscIdAsc(Collection<UUID> dispatchIds);

    /**
     * 특정 dispatch가 지금까지 몇 번 시도되었는지 센다.
     * 다음 attempt 번호 계산이나 재시도 정책 판단의 기준이 된다.
     */
    long countByNotificationDispatchId(UUID notificationDispatchId);
}

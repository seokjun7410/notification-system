package com.notification.mid.notification.application.port.out;

import java.time.LocalDateTime;
import java.util.List;

import com.notification.mid.notification.domain.dispatch.NotificationDispatch;

/**
 * 비동기 worker/recovery가 큐처럼 dispatch를 훑어볼 때 사용하는 polling 전용 outbound port다.
 * 일반 조회 포트와 분리해, 배치 조회와 잠금 전략이 비동기 처리 책임임을 드러낸다.
 */
public interface NotificationDispatchPollingPort {

    /**
     * 현재 시점에 worker가 선점할 수 있는 dispatch 후보를 조회한다.
     * 대상은 PENDING 또는 재시도 시각이 지난 RETRY_WAIT이며, 구현체는 중복 선점을 막는 잠금 전략을 가질 수 있다.
     */
    List<NotificationDispatch> findDispatchTargets(LocalDateTime now, int batchSize);

    /**
     * recovery가 회수할 오래된 PROCESSING dispatch 후보를 조회한다.
     * worker 비정상 종료나 장시간 정체 건을 운영 확인 가능한 상태로 전환할 때 사용한다.
     */
    List<NotificationDispatch> findStaleProcessingRecoveryTargets(LocalDateTime threshold, int batchSize);
}

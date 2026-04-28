package com.notification.mid.notification.application.port.out;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.notification.mid.notification.domain.dispatch.NotificationChannel;
import com.notification.mid.notification.domain.dispatch.NotificationDispatch;

/**
 * dispatch를 조회 목적으로 읽어오는 outbound port다.
 * 등록 중복 확인, 상세 조회, 목록 응답 조립처럼 상태를 바꾸지 않는 유스케이스가 이 포트를 사용한다.
 */
public interface NotificationDispatchQueryPort {

    /**
     * dispatch ID로 단건 조회한다.
     * 발송 처리나 상태 조회에서 특정 채널 발송 건을 다시 확인할 때 사용한다.
     */
    Optional<NotificationDispatch> findById(UUID dispatchId);

    /**
     * 하나의 알림 원본에 대해 특정 채널 dispatch가 이미 존재하는지 조회한다.
     * 채널 중복 생성 방지의 핵심 조회다.
     */
    Optional<NotificationDispatch> findByNotificationIdAndChannel(UUID notificationId, NotificationChannel channel);

    /**
     * 하나의 알림 원본에 연결된 dispatch를 생성 순서대로 조회한다.
     * 상세 조회 응답에서 채널별 상태를 묶어 보여줄 때 사용한다.
     */
    List<NotificationDispatch> findByNotificationIdOrderByCreatedAtAscIdAsc(UUID notificationId);

    /**
     * 여러 알림 원본의 dispatch를 한 번에 조회한다.
     * 목록 조회에서 notification 여러 건에 대한 채널 상태를 배치로 조립할 때 사용한다.
     */
    List<NotificationDispatch> findByNotificationIdInOrderByCreatedAtAscIdAsc(Collection<UUID> notificationIds);
}

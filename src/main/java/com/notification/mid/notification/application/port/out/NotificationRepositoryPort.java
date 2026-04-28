package com.notification.mid.notification.application.port.out;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.notification.mid.notification.domain.notification.Notification;
import com.notification.mid.notification.domain.notification.NotificationType;

public interface NotificationRepositoryPort {

    /**
     * 알림 원본 ID로 단건 조회한다.
     * 상세 조회나 후속 dispatch/attempt 조합의 시작점이 되는 조회다.
     */
    Optional<Notification> findById(UUID notificationId);

    /**
     * 멱등성 키인 (eventId, recipientId, type) 기준으로 기존 알림 원본을 조회한다.
     * 등록 유스케이스는 이 조회 결과로 신규 생성인지 기존 재사용인지 판단한다.
     */
    Optional<Notification> findByEventIdAndRecipientIdAndType(
            String eventId,
            String recipientId,
            NotificationType type
    );

    /**
     * 알림 원본을 저장하고 즉시 flush한다.
     * 등록 직후 unique 제약 위반이나 후속 dispatch 생성에 필요한 식별자를 빠르게 확정할 때 사용한다.
     */
    Notification saveAndFlush(Notification notification);

    /**
     * 여러 notificationId에 해당하는 원본 알림을 한 번에 조회한다.
     * 목록 조회에서 dispatch 결과와 합쳐 응답을 구성할 때 사용한다.
     */
    List<Notification> findAllById(Iterable<UUID> notificationIds);

    /**
     * 관리자용 알림 목록의 다음 페이지 후보 notificationId를 커서 기반으로 조회한다.
     * 본문과 dispatch 상세는 후속 배치 조회에서 채운다.
     */
    List<String> searchCursorPageNotificationIds(
            String recipientId,
            LocalDateTime cursorCreatedAt,
            String cursorId,
            int limit
    );

    /**
     * 관리자용 목록에서 channel/read 조건을 적용한 notificationId 페이지를 조회한다.
     * 먼저 ID 범위를 좁힌 뒤, 실제 응답 조립은 notification/dispatch 배치 조회로 이어진다.
     */
    List<String> searchCursorPageNotificationIdsByChannel(
            String recipientId,
            String channel,
            Boolean readFilter,
            LocalDateTime cursorCreatedAt,
            String cursorId,
            int limit
    );

    /**
     * 사용자용 IN_APP 목록의 다음 페이지 후보 notificationId를 조회한다.
     * 사용자 조회는 발송 완료된 IN_APP만 대상으로 하므로 별도 전용 쿼리 포트를 둔다.
     */
    List<String> searchUserInAppCursorPageNotificationIds(
            String recipientId,
            Boolean readFilter,
            LocalDateTime cursorCreatedAt,
            String cursorId,
            int limit
    );
}

package com.notification.mid.notification.infrastructure.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.notification.mid.notification.domain.notification.Notification;
import com.notification.mid.notification.domain.notification.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaNotificationRepository extends JpaRepository<Notification, UUID> {

    /**
     * 동일한 eventId, recipientId, type 조합의 알림 존재 여부를 조회한다.
     */
    Optional<Notification> findByEventIdAndRecipientIdAndType(
            String eventId,
            String recipientId,
            NotificationType type
    );

    /**
     * 채널 필터가 없는 커서 기반 목록 조회용 notification ID를 가져온다.
     * created_at, id 내림차순 기준으로 keyset pagination을 적용한다.
     */
    @Query(value = """
            select cast(n.id as varchar)
            from notification n
            where n.recipient_id = :recipientId
              and (
                  :cursorCreatedAt is null
                  or n.created_at < :cursorCreatedAt
                  or (n.created_at = :cursorCreatedAt and cast(n.id as varchar) < :cursorId)
              )
            order by n.created_at desc, n.id desc
            limit :limit
            """, nativeQuery = true)
    List<String> searchCursorPageNotificationIds(
            @Param("recipientId") String recipientId,
            @Param("cursorCreatedAt") LocalDateTime cursorCreatedAt,
            @Param("cursorId") String cursorId,
            @Param("limit") int limit
    );

    /**
     * 채널 필터가 있는 커서 기반 목록 조회용 notification ID를 가져온다.
     * notification_dispatch를 inner join 하여 조건에 맞는 알림만 대상으로 삼는다.
     */
    @Query(value = """
            select cast(n.id as varchar)
            from notification n
            join notification_dispatch d
              on d.notification_id = n.id
             and d.channel = :channel
            where n.recipient_id = :recipientId
              and (
                  :readFilter is null
                  or (:readFilter = true and d.read_at is not null)
                  or (:readFilter = false and d.read_at is null)
              )
              and (
                  :cursorCreatedAt is null
                  or n.created_at < :cursorCreatedAt
                  or (n.created_at = :cursorCreatedAt and cast(n.id as varchar) < :cursorId)
              )
            order by n.created_at desc, n.id desc
            limit :limit
            """, nativeQuery = true)
    List<String> searchCursorPageNotificationIdsByChannel(
            @Param("recipientId") String recipientId,
            @Param("channel") String channel,
            @Param("readFilter") Boolean readFilter,
            @Param("cursorCreatedAt") LocalDateTime cursorCreatedAt,
            @Param("cursorId") String cursorId,
            @Param("limit") int limit
    );

    /**
     * 사용자용 IN_APP 발송 완료 알림 목록의 notification ID를 가져온다.
     */
    @Query(value = """
            select cast(n.id as varchar)
            from notification n
            join notification_dispatch d
              on d.notification_id = n.id
             and d.channel = 'IN_APP'
             and d.status = 'SUCCESS'
            where n.recipient_id = :recipientId
              and (
                  :readFilter is null
                  or (:readFilter = true and d.read_at is not null)
                  or (:readFilter = false and d.read_at is null)
              )
              and (
                  :cursorCreatedAt is null
                  or n.created_at < :cursorCreatedAt
                  or (n.created_at = :cursorCreatedAt and cast(n.id as varchar) < :cursorId)
              )
            order by n.created_at desc, n.id desc
            limit :limit
            """, nativeQuery = true)
    List<String> searchUserInAppCursorPageNotificationIds(
            @Param("recipientId") String recipientId,
            @Param("readFilter") Boolean readFilter,
            @Param("cursorCreatedAt") LocalDateTime cursorCreatedAt,
            @Param("cursorId") String cursorId,
            @Param("limit") int limit
    );
}

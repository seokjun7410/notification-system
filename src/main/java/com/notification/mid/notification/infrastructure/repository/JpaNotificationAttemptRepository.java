package com.notification.mid.notification.infrastructure.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import com.notification.mid.notification.domain.attempt.NotificationAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaNotificationAttemptRepository extends JpaRepository<NotificationAttempt, UUID> {

    /**
     * 특정 dispatch의 발송 시도 이력을 순서대로 조회한다.
     */
    List<NotificationAttempt> findByNotificationDispatchIdOrderByAttemptNoAscIdAsc(UUID notificationDispatchId);

    /**
     * 여러 dispatch의 발송 시도 이력을 dispatch별 순서대로 조회한다.
     */
    @Query("""
            select a
            from NotificationAttempt a
            where a.notificationDispatch.id in :dispatchIds
            order by a.notificationDispatch.id asc, a.attemptNo asc, a.id asc
            """)
    List<NotificationAttempt> findByNotificationDispatchIdInOrderByAttemptNoAscIdAsc(
            @Param("dispatchIds") Collection<UUID> dispatchIds
    );

    /**
     * 특정 dispatch의 발송 시도 횟수를 조회한다.
     */
    long countByNotificationDispatchId(UUID notificationDispatchId);
}

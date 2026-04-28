package com.notification.mid.notification.infrastructure.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.notification.mid.notification.domain.dispatch.NotificationChannel;
import com.notification.mid.notification.domain.dispatch.NotificationDispatch;
import com.notification.mid.notification.domain.dispatch.NotificationDispatchStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaNotificationDispatchRepository extends JpaRepository<NotificationDispatch, UUID> {

    Optional<NotificationDispatch> findById(UUID id);

    /**
     * 특정 알림 원본과 채널 조합의 dispatch 존재 여부를 조회한다.
     */
    Optional<NotificationDispatch> findByNotificationIdAndChannel(UUID notificationId, NotificationChannel channel);

    /**
     * 특정 알림 원본에 속한 dispatch 목록을 생성 순으로 조회한다.
     */
    List<NotificationDispatch> findByNotificationIdOrderByCreatedAtAscIdAsc(UUID notificationId);

    /**
     * 여러 알림 원본에 속한 dispatch 목록을 생성 순으로 조회한다.
     */
    List<NotificationDispatch> findByNotificationIdInOrderByCreatedAtAscIdAsc(Collection<UUID> notificationIds);

    /**
     * worker가 선점할 처리 대상 dispatch를 조회한다.
     * PENDING 또는 재시도 가능 시간이 지난 RETRY_WAIT 상태만 대상이다.
     */
    @Query(value = """
            select *
            from notification_dispatch
            where status = 'PENDING'
               or (status = 'RETRY_WAIT' and next_retry_at <= :now)
            order by created_at asc, id asc
            for update skip locked
            """, nativeQuery = true)
    List<NotificationDispatch> findDispatchTargetsPage(@Param("now") LocalDateTime now, Pageable pageable);

    default List<NotificationDispatch> findDispatchTargets(LocalDateTime now, int batchSize) {
        return findDispatchTargetsPage(now, PageRequest.of(0, batchSize));
    }

    /**
     * recovery가 선점할 오래된 PROCESSING dispatch를 조회한다.
     * 여러 recovery 인스턴스가 동시에 실행돼도 같은 row를 중복 복구하지 않도록 잠금을 건다.
     */
    @Query(value = """
            select *
            from notification_dispatch
            where status = 'PROCESSING'
              and updated_at < :threshold
            order by updated_at asc, id asc
            for update skip locked
            """, nativeQuery = true)
    List<NotificationDispatch> findStaleProcessingRecoveryTargetsPage(
            @Param("threshold") LocalDateTime threshold,
            Pageable pageable
    );

    default List<NotificationDispatch> findStaleProcessingRecoveryTargets(LocalDateTime threshold, int batchSize) {
        return findStaleProcessingRecoveryTargetsPage(threshold, PageRequest.of(0, batchSize));
    }

    /**
     * 실제 외부 채널 발송을 시작하기 직전에 sendStartedAt을 기록한다.
     * 이미 recovery가 개입했거나 다른 worker가 처리한 row는 조건에서 걸러진다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update NotificationDispatch d
            set d.sendStartedAt = :sendStartedAt,
                d.updatedAt = :updatedAt
            where d.id = :dispatchId
              and d.status = com.notification.mid.notification.domain.dispatch.NotificationDispatchStatus.PROCESSING
              and d.sendStartedAt is null
            """)
    int markSendStarted(
            @Param("dispatchId") UUID dispatchId,
            @Param("sendStartedAt") LocalDateTime sendStartedAt,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    /**
     * 기대한 현재 상태와 일치할 때만 dispatch 상태를 원자적으로 갱신한다.
     * worker와 recovery가 경합하더라도 마지막 쓰기가 이전 상태를 덮어쓰지 않도록 조건을 둔다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update NotificationDispatch d
            set d.status = :nextStatus,
                d.retryCount = :retryCount,
                d.nextRetryAt = :nextRetryAt,
                d.lastError = :lastError,
                d.sendStartedAt = :sendStartedAt,
                d.updatedAt = :updatedAt
            where d.id = :dispatchId
              and d.status = :expectedCurrentStatus
            """)
    int applyStateChange(
            @Param("dispatchId") UUID dispatchId,
            @Param("expectedCurrentStatus") NotificationDispatchStatus expectedCurrentStatus,
            @Param("nextStatus") NotificationDispatchStatus nextStatus,
            @Param("retryCount") int retryCount,
            @Param("nextRetryAt") LocalDateTime nextRetryAt,
            @Param("lastError") String lastError,
            @Param("sendStartedAt") LocalDateTime sendStartedAt,
            @Param("updatedAt") LocalDateTime updatedAt
    );
}

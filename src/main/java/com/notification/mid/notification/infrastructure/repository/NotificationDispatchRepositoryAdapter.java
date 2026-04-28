package com.notification.mid.notification.infrastructure.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.notification.mid.notification.application.port.out.NotificationDispatchCommandPort;
import com.notification.mid.notification.application.port.out.NotificationDispatchPollingPort;
import com.notification.mid.notification.application.port.out.NotificationDispatchQueryPort;
import com.notification.mid.notification.domain.dispatch.NotificationChannel;
import com.notification.mid.notification.domain.dispatch.NotificationDispatch;
import com.notification.mid.notification.domain.dispatch.NotificationDispatchStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class NotificationDispatchRepositoryAdapter implements
        NotificationDispatchQueryPort,
        NotificationDispatchCommandPort,
        NotificationDispatchPollingPort {

    private final JpaNotificationDispatchRepository notificationDispatchJpaRepository;

    @Override
    public Optional<NotificationDispatch> findById(UUID dispatchId) {
        return notificationDispatchJpaRepository.findById(dispatchId);
    }

    @Override
    public Optional<NotificationDispatch> findByNotificationIdAndChannel(UUID notificationId, NotificationChannel channel) {
        return notificationDispatchJpaRepository.findByNotificationIdAndChannel(notificationId, channel);
    }

    @Override
    public NotificationDispatch saveAndFlush(NotificationDispatch notificationDispatch) {
        return notificationDispatchJpaRepository.saveAndFlush(notificationDispatch);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markSendStarted(UUID dispatchId, LocalDateTime sendStartedAt, LocalDateTime updatedAt) {
        return notificationDispatchJpaRepository.markSendStarted(dispatchId, sendStartedAt, updatedAt) > 0;
    }

    @Override
    public List<NotificationDispatch> findByNotificationIdOrderByCreatedAtAscIdAsc(UUID notificationId) {
        return notificationDispatchJpaRepository.findByNotificationIdOrderByCreatedAtAscIdAsc(notificationId);
    }

    @Override
    public List<NotificationDispatch> findByNotificationIdInOrderByCreatedAtAscIdAsc(Collection<UUID> notificationIds) {
        return notificationDispatchJpaRepository.findByNotificationIdInOrderByCreatedAtAscIdAsc(notificationIds);
    }

    @Override
    public List<NotificationDispatch> findDispatchTargets(LocalDateTime now, int batchSize) {
        return notificationDispatchJpaRepository.findDispatchTargets(now, batchSize);
    }

    @Override
    public List<NotificationDispatch> findStaleProcessingRecoveryTargets(LocalDateTime threshold, int batchSize) {
        return notificationDispatchJpaRepository.findStaleProcessingRecoveryTargets(threshold, batchSize);
    }

    @Override
    public boolean applyStateChange(
            UUID dispatchId,
            NotificationDispatchStatus expectedCurrentStatus,
            NotificationDispatchStatus nextStatus,
            int retryCount,
            LocalDateTime nextRetryAt,
            String lastError,
            LocalDateTime sendStartedAt,
            LocalDateTime updatedAt
    ) {
        return notificationDispatchJpaRepository.applyStateChange(
                dispatchId,
                expectedCurrentStatus,
                nextStatus,
                retryCount,
                nextRetryAt,
                lastError,
                sendStartedAt,
                updatedAt
        ) > 0;
    }
}

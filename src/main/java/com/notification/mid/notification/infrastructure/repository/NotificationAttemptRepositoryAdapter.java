package com.notification.mid.notification.infrastructure.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import com.notification.mid.notification.application.port.out.NotificationAttemptRepositoryPort;
import com.notification.mid.notification.domain.attempt.NotificationAttempt;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class NotificationAttemptRepositoryAdapter implements NotificationAttemptRepositoryPort {

    private final JpaNotificationAttemptRepository notificationAttemptJpaRepository;

    @Override
    public NotificationAttempt save(NotificationAttempt notificationAttempt) {
        return notificationAttemptJpaRepository.save(notificationAttempt);
    }

    @Override
    public List<NotificationAttempt> findByNotificationDispatchIdInOrderByAttemptNoAscIdAsc(Collection<UUID> dispatchIds) {
        return notificationAttemptJpaRepository.findByNotificationDispatchIdInOrderByAttemptNoAscIdAsc(dispatchIds);
    }

    @Override
    public long countByNotificationDispatchId(UUID notificationDispatchId) {
        return notificationAttemptJpaRepository.countByNotificationDispatchId(notificationDispatchId);
    }
}

package com.notification.mid.notification.infrastructure.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.notification.mid.notification.application.port.out.NotificationRepositoryPort;
import com.notification.mid.notification.domain.notification.Notification;
import com.notification.mid.notification.domain.notification.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class NotificationRepositoryAdapter implements NotificationRepositoryPort {

    private final JpaNotificationRepository notificationJpaRepository;

    @Override
    public Optional<Notification> findById(UUID notificationId) {
        return notificationJpaRepository.findById(notificationId);
    }

    @Override
    public Optional<Notification> findByEventIdAndRecipientIdAndType(
            String eventId,
            String recipientId,
            NotificationType type
    ) {
        return notificationJpaRepository.findByEventIdAndRecipientIdAndType(eventId, recipientId, type);
    }

    @Override
    public Notification saveAndFlush(Notification notification) {
        return notificationJpaRepository.saveAndFlush(notification);
    }

    @Override
    public List<Notification> findAllById(Iterable<UUID> notificationIds) {
        return notificationJpaRepository.findAllById(notificationIds);
    }

    @Override
    public List<String> searchCursorPageNotificationIds(
            String recipientId,
            LocalDateTime cursorCreatedAt,
            String cursorId,
            int limit
    ) {
        return notificationJpaRepository.searchCursorPageNotificationIds(
                recipientId,
                cursorCreatedAt,
                cursorId,
                limit
        );
    }

    @Override
    public List<String> searchCursorPageNotificationIdsByChannel(
            String recipientId,
            String channel,
            Boolean readFilter,
            LocalDateTime cursorCreatedAt,
            String cursorId,
            int limit
    ) {
        return notificationJpaRepository.searchCursorPageNotificationIdsByChannel(
                recipientId,
                channel,
                readFilter,
                cursorCreatedAt,
                cursorId,
                limit
        );
    }

    @Override
    public List<String> searchUserInAppCursorPageNotificationIds(
            String recipientId,
            Boolean readFilter,
            LocalDateTime cursorCreatedAt,
            String cursorId,
            int limit
    ) {
        return notificationJpaRepository.searchUserInAppCursorPageNotificationIds(
                recipientId,
                readFilter,
                cursorCreatedAt,
                cursorId,
                limit
        );
    }
}

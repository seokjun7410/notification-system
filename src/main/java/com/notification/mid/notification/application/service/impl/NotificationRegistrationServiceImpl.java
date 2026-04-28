package com.notification.mid.notification.application.service.impl;

import com.notification.mid.common.time.TimeProvider;
import com.notification.mid.notification.application.dto.response.NotificationCreateRes;
import com.notification.mid.notification.application.port.out.NotificationDispatchCommandPort;
import com.notification.mid.notification.application.port.out.NotificationDispatchQueryPort;
import com.notification.mid.notification.application.port.out.NotificationRepositoryPort;
import com.notification.mid.notification.application.port.in.NotificationRegistrationService;
import com.notification.mid.common.exception.IdempotencyPayloadMismatchException;
import com.notification.mid.notification.domain.dispatch.NotificationChannel;
import com.notification.mid.notification.domain.dispatch.NotificationDispatch;
import com.notification.mid.notification.domain.notification.Notification;
import com.notification.mid.notification.domain.notification.NotificationType;
import com.notification.mid.common.exception.DuplicateNotificationRequestException;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
@Service
@Transactional(readOnly = true)
public class NotificationRegistrationServiceImpl implements NotificationRegistrationService {

    private final NotificationRepositoryPort notificationRepository;
    private final NotificationDispatchQueryPort notificationDispatchQueryPort;
    private final NotificationDispatchCommandPort notificationDispatchCommandPort;
    private final TimeProvider timeProvider;
    private final EntityManager entityManager;

    @Override
    @Transactional
    public NotificationCreateRes register(
            String eventId,
            String recipientId,
            NotificationType type,
            NotificationChannel channel,
            String title,
            String content
    ) {
        Notification notification = findOrCreateNotification(eventId, recipientId, type, title, content);
        NotificationDispatch notificationDispatch = saveNotificationDispatch(notification, channel);
        return NotificationCreateRes.from(notification, notificationDispatch);
    }

    private Notification findOrCreateNotification(
            String eventId,
            String recipientId,
            NotificationType type,
            String title,
            String content
    ) {
        return notificationRepository.findByEventIdAndRecipientIdAndType(eventId, recipientId, type)
                .map(notification -> validatePayloadAndReuse(notification, title, content))
                .orElseGet(() -> createNotification(eventId, recipientId, type, title, content));
    }

    private Notification validatePayloadAndReuse(
            Notification notification,
            String title,
            String content
    ) {
        if (!notification.hasSamePayload(title, content)) {
            throw new IdempotencyPayloadMismatchException();
        }

        return notification;
    }

    private Notification createNotification(
            String eventId,
            String recipientId,
            NotificationType type,
            String title,
            String content
    ) {
        Notification notification = Notification.create(
                eventId,
                recipientId,
                type,
                title,
                content,
                timeProvider.now()
        );

        try {
            return notificationRepository.saveAndFlush(notification);
        } catch (DataIntegrityViolationException exception) {
            entityManager.clear();
            return notificationRepository.findByEventIdAndRecipientIdAndType(eventId, recipientId, type)
                    .map(existingNotification -> validatePayloadAndReuse(existingNotification, title, content))
                    .orElseThrow(() -> exception);
        }
    }

    private NotificationDispatch saveNotificationDispatch(Notification notification, NotificationChannel channel) {
        UUID notificationId = notification.getId();

        if (notificationDispatchQueryPort.findByNotificationIdAndChannel(notificationId, channel).isPresent()) {
            throw new DuplicateNotificationRequestException();
        }

        NotificationDispatch notificationDispatch = NotificationDispatch.create(notification, channel, timeProvider.now());

        try {
            return notificationDispatchCommandPort.saveAndFlush(notificationDispatch);
        } catch (DataIntegrityViolationException exception) {
            log.warn("중복 알림 요청이 감지되었습니다. notificationId={}, channel={}", notificationId, channel);
            throw new DuplicateNotificationRequestException();
        }
    }
}

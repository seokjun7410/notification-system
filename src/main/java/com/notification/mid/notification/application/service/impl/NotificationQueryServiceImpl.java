package com.notification.mid.notification.application.service.impl;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.notification.mid.common.exception.shared.BusinessException;
import com.notification.mid.common.exception.shared.ErrorCode;
import com.notification.mid.notification.application.dto.response.NotificationDetailRes;
import com.notification.mid.notification.application.dto.response.NotificationDispatchDetailRes;
import com.notification.mid.notification.application.dto.response.NotificationDispatchSummaryRes;
import com.notification.mid.notification.application.dto.response.NotificationPageRes;
import com.notification.mid.notification.application.dto.response.NotificationSummaryRes;
import com.notification.mid.notification.application.dto.response.UserNotificationPageRes;
import com.notification.mid.notification.application.dto.response.UserNotificationSummaryRes;
import com.notification.mid.notification.application.port.out.NotificationAttemptRepositoryPort;
import com.notification.mid.notification.application.port.out.NotificationDispatchQueryPort;
import com.notification.mid.notification.application.port.out.NotificationRepositoryPort;
import com.notification.mid.notification.application.port.in.NotificationQueryService;
import com.notification.mid.notification.domain.attempt.NotificationAttempt;
import com.notification.mid.notification.domain.dispatch.NotificationChannel;
import com.notification.mid.notification.domain.dispatch.NotificationDispatch;
import com.notification.mid.notification.domain.dispatch.NotificationReadStatus;
import com.notification.mid.notification.domain.notification.Notification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Service
@Transactional(readOnly = true)
public class NotificationQueryServiceImpl implements NotificationQueryService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final DateTimeFormatter CURSOR_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final NotificationRepositoryPort notificationRepository;
    private final NotificationDispatchQueryPort notificationDispatchQueryPort;
    private final NotificationAttemptRepositoryPort notificationAttemptRepository;

    @Override
    public NotificationDetailRes getNotification(UUID notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND));
        List<NotificationDispatch> notificationDispatches = notificationDispatchQueryPort
                .findByNotificationIdOrderByCreatedAtAscIdAsc(notificationId);
        Map<UUID, List<NotificationAttempt>> attemptMap = getAttemptMap(notificationDispatches);
        List<NotificationDispatchDetailRes> dispatches = notificationDispatches.stream()
                .map(notificationDispatch -> NotificationDispatchDetailRes.from(
                        notificationDispatch,
                        attemptMap.getOrDefault(notificationDispatch.getId(), List.of())
                ))
                .toList();

        return NotificationDetailRes.from(notification, dispatches);
    }

    @Override
    public NotificationPageRes searchAdmin(
            String recipientId,
            NotificationChannel channel,
            NotificationReadStatus readStatus,
            int size,
            String cursor
    ) {
        // 관리자 조회에서 허용되지 않는 필터 조합이 있는지 먼저 검증한다.
        validateSearchFilter(channel, readStatus);

        // 요청 size를 서비스 정책에 맞게 보정한다.
        int normalizedSize = normalizeSize(size);

        // 클라이언트가 전달한 cursor 문자열을 조회에 사용할 커서 객체로 변환한다.
        NotificationCursor notificationCursor = parseCursor(cursor);

        // 읽음 상태 필터를 Boolean 값으로 변환한다.
        // null이면 전체, READ이면 true, UNREAD이면 false로 조회한다.
        Boolean readFilter = readStatus == null ? null : readStatus == NotificationReadStatus.READ;

        // 조건에 맞는 알림 ID를 먼저 조회한다.
        // normalizedSize + 1개를 조회하는 이유는 다음 페이지 존재 여부를 판단하기 위해서다.
        List<UUID> notificationIds = getNotificationIds(
                recipientId,
                channel,
                readFilter,
                notificationCursor,
                normalizedSize + 1
        ).stream()
                // Redis나 Query 결과가 문자열 UUID라면 UUID 객체로 변환한다.
                .map(UUID::fromString)
                .toList();

        // 요청한 size보다 1개 더 조회됐다면 다음 페이지가 존재한다.
        boolean hasNext = notificationIds.size() > normalizedSize;

        // 실제 응답에 담을 ID 목록은 normalizedSize까지만 사용한다.
        // 마지막 1개는 hasNext 판단용이므로 응답에서는 제외한다.
        List<UUID> pageNotificationIds = hasNext
                ? notificationIds.subList(0, normalizedSize)
                : notificationIds;

        // ID 목록에 해당하는 Notification 엔티티를 조회한다.
        List<Notification> notifications = getNotificationsInOrder(pageNotificationIds);

        // 알림 ID별 Dispatch 목록을 한 번에 조회해서 Map으로 묶는다.
        Map<UUID, List<NotificationDispatch>> dispatchMap = getDispatchMap(pageNotificationIds);

        // 조회된 Notification을 관리자 응답 DTO로 변환한다.
        List<NotificationSummaryRes> content = notifications.stream()
                .map(notification -> NotificationSummaryRes.from(
                        notification,

                        // 현재 알림에 연결된 Dispatch 중
                        // 요청한 channel/readStatus 조건에 맞는 Dispatch만 응답에 포함한다.
                        getFilteredDispatches(dispatchMap.get(notification.getId()), channel, readStatus)
                ))
                .toList();

        // 다음 페이지 요청에 사용할 cursor를 만든다.
        // 다음 페이지가 있고, 현재 페이지에 데이터가 있을 때만 마지막 알림 기준으로 cursor를 생성한다.
        String nextCursor = hasNext && !notifications.isEmpty()
                ? encodeCursor(notifications.getLast())
                : null;

        // 최종 페이지 응답을 생성한다.
        return NotificationPageRes.of(
                content,
                normalizedSize,
                hasNext,
                nextCursor
        );
    }

    @Override
    public UserNotificationPageRes searchUserInApp(
            String recipientId,
            NotificationReadStatus readStatus,
            int size,
            String cursor
    ) {
        // 사용자 인앱 알림 조회에서 허용되지 않는 필터가 있는지 검증한다.
        validateUserSearchFilter(readStatus);

        // 요청 size를 서비스 정책에 맞게 보정한다.
        int normalizedSize = normalizeSize(size);

        // 전달받은 cursor 문자열을 커서 객체로 변환한다.
        NotificationCursor notificationCursor = parseCursor(cursor);

        // 읽음 상태 조건을 Boolean 필터로 변환한다.
        // null이면 전체, READ이면 true, UNREAD이면 false로 조회한다.
        Boolean readFilter = readStatus == null ? null : readStatus == NotificationReadStatus.READ;

        // 사용자 인앱 알림 ID를 먼저 조회한다.
        // normalizedSize + 1개를 조회해서 다음 페이지 존재 여부를 판단한다.
        List<UUID> notificationIds = getUserInAppNotificationIds(
                recipientId,
                readFilter,
                notificationCursor,
                normalizedSize + 1
        ).stream()
                // 문자열 UUID를 UUID 객체로 변환한다.
                .map(UUID::fromString)
                .toList();

        // 요청 size보다 많이 조회되면 다음 페이지가 존재한다.
        boolean hasNext = notificationIds.size() > normalizedSize;

        // 응답에 포함할 알림 ID만 잘라낸다.
        // 초과 조회된 1개는 다음 페이지 여부 판단용이라 제외한다.
        List<UUID> pageNotificationIds = hasNext
                ? notificationIds.subList(0, normalizedSize)
                : notificationIds;

        // 알림 ID 목록에 해당하는 Notification을 조회한다.
        // ID 조회 순서와 응답 순서가 일치하도록 정렬해서 가져온다.
        List<Notification> notifications = getNotificationsInOrder(pageNotificationIds);

        // 사용자용 응답 DTO로 변환한다.
        // 사용자 조회는 IN_APP 알림만 대상으로 하므로 Dispatch 정보를 별도로 내려주지 않는다.
        List<UserNotificationSummaryRes> content = notifications.stream()
                .map(UserNotificationSummaryRes::from)
                .toList();

        // 다음 페이지가 있으면 현재 페이지의 마지막 알림을 기준으로 cursor를 생성한다.
        String nextCursor = hasNext && !notifications.isEmpty()
                ? encodeCursor(notifications.getLast())
                : null;

        // 사용자 알림 페이지 응답을 생성한다.
        return UserNotificationPageRes.of(
                content,
                normalizedSize,
                hasNext,
                nextCursor
        );
    }

    private List<String> getNotificationIds(
            String recipientId,
            NotificationChannel channel,
            Boolean readFilter,
            NotificationCursor notificationCursor,
            int limit
    ) {
        LocalDateTime cursorCreatedAt = notificationCursor == null ? null : notificationCursor.createdAt();
        String cursorId = notificationCursor == null ? null : notificationCursor.id().toString();

        if (channel == null) {
            return notificationRepository.searchCursorPageNotificationIds(
                    recipientId,
                    cursorCreatedAt,
                    cursorId,
                    limit
            );
        }

        return notificationRepository.searchCursorPageNotificationIdsByChannel(
                recipientId,
                channel.name(),
                readFilter,
                cursorCreatedAt,
                cursorId,
                limit
        );
    }

    private List<String> getUserInAppNotificationIds(
            String recipientId,
            Boolean readFilter,
            NotificationCursor notificationCursor,
            int limit
    ) {
        LocalDateTime cursorCreatedAt = notificationCursor == null ? null : notificationCursor.createdAt();
        String cursorId = notificationCursor == null ? null : notificationCursor.id().toString();

        return notificationRepository.searchUserInAppCursorPageNotificationIds(
                recipientId,
                readFilter,
                cursorCreatedAt,
                cursorId,
                limit
        );
    }

    private void validateSearchFilter(NotificationChannel channel, NotificationReadStatus readStatus) {
        if (readStatus == null) {
            return;
        }

        if (channel == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "readStatus는 channel=IN_APP와 함께 요청해야 합니다.");
        }

        if (channel != NotificationChannel.IN_APP) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "readStatus는 IN_APP 채널에서만 사용할 수 있습니다.");
        }

        if (readStatus == NotificationReadStatus.UNKNOWN) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "readStatus는 READ 또는 UNREAD만 사용할 수 있습니다.");
        }
    }

    private void validateUserSearchFilter(NotificationReadStatus readStatus) {
        if (readStatus == null) {
            return;
        }

        if (readStatus == NotificationReadStatus.UNKNOWN) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "readStatus는 READ 또는 UNREAD만 사용할 수 있습니다.");
        }
    }

    private Map<UUID, List<NotificationDispatch>> getDispatchMap(List<UUID> notificationIds) {
        if (notificationIds.isEmpty()) {
            return Collections.emptyMap();
        }

        return notificationDispatchQueryPort.findByNotificationIdInOrderByCreatedAtAscIdAsc(notificationIds)
                .stream()
                .collect(Collectors.groupingBy(
                        notificationDispatch -> notificationDispatch.getNotification().getId(),
                        Collectors.mapping(Function.identity(), Collectors.toList())
                ));
    }

    private List<Notification> getNotificationsInOrder(List<UUID> notificationIds) {
        if (notificationIds.isEmpty()) {
            return List.of();
        }

        Map<UUID, Notification> notificationMap = notificationRepository.findAllById(notificationIds)
                .stream()
                .collect(Collectors.toMap(Notification::getId, Function.identity()));

        return notificationIds.stream()
                .map(notificationMap::get)
                .filter(Objects::nonNull)
                .toList();
    }

    private Map<UUID, List<NotificationAttempt>> getAttemptMap(List<NotificationDispatch> notificationDispatches) {
        if (notificationDispatches.isEmpty()) {
            return Collections.emptyMap();
        }

        List<UUID> dispatchIds = notificationDispatches.stream()
                .map(NotificationDispatch::getId)
                .toList();

        return notificationAttemptRepository.findByNotificationDispatchIdInOrderByAttemptNoAscIdAsc(dispatchIds)
                .stream()
                .collect(Collectors.groupingBy(
                        notificationAttempt -> notificationAttempt.getNotificationDispatch().getId(),
                        Collectors.mapping(Function.identity(), Collectors.toList())
                ));
    }

    private List<NotificationDispatchSummaryRes> getFilteredDispatches(
            List<NotificationDispatch> notificationDispatches,
            NotificationChannel channel,
            NotificationReadStatus readStatus
    ) {
        if (notificationDispatches == null) {
            return List.of();
        }

        return notificationDispatches.stream()
                .filter(notificationDispatch -> channel == null || notificationDispatch.getChannel() == channel)
                .filter(notificationDispatch -> readStatus == null || notificationDispatch.getReadStatus() == readStatus)
                .map(NotificationDispatchSummaryRes::from)
                .toList();
    }

    private int normalizeSize(int size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }

        return Math.min(size, MAX_PAGE_SIZE);
    }

    private NotificationCursor parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }

        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = decoded.split("\\|", 2);

            if (parts.length != 2) {
                throw new IllegalArgumentException("cursor parts");
            }

            return new NotificationCursor(
                    LocalDateTime.parse(parts[0], CURSOR_TIME_FORMATTER),
                    UUID.fromString(parts[1])
            );
        } catch (IllegalArgumentException | DateTimeParseException exception) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "cursor 형식이 올바르지 않습니다.");
        }
    }

    private String encodeCursor(Notification notification) {
        String payload = notification.getCreatedAt().format(CURSOR_TIME_FORMATTER)
                + "|" + notification.getId();
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    private record NotificationCursor(
            LocalDateTime createdAt,
            UUID id
    ) {
    }
}

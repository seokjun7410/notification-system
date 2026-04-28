package com.notification.mid.notification.domain.attempt;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import com.notification.mid.notification.domain.dispatch.NotificationDispatch;
import com.notification.mid.notification.domain.shared.BaseEntity;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.id.uuid.UuidVersion7Strategy;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "notification_attempt")
public class NotificationAttempt extends BaseEntity {

    @Id
    @GeneratedValue
    @UuidGenerator(algorithm = UuidVersion7Strategy.class)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dispatch_id", nullable = false)
    private NotificationDispatch notificationDispatch;

    @Column(name = "attempt_no", nullable = false)
    private int attemptNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "result_status", nullable = false, length = 30)
    private NotificationAttemptResultStatus resultStatus;

    @Column(name = "failure_message", columnDefinition = "text")
    private String failureMessage;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "responded_at", nullable = false)
    private LocalDateTime respondedAt;

    /**
     * 한 번의 발송 시도가 성공으로 끝난 이력을 생성한다.
     * dispatch의 최종 상태와 별개로, 시도 단위의 성공 사실을 append-only로 남긴다.
     */
    public static NotificationAttempt success(
            NotificationDispatch notificationDispatch,
            int attemptNo,
            LocalDateTime requestedAt,
            LocalDateTime respondedAt
    ) {
        return create(notificationDispatch, attemptNo, NotificationAttemptResultStatus.SUCCESS, null, requestedAt, respondedAt);
    }

    /**
     * 재시도 가능한 일반 실패 이력을 생성한다.
     * 실패 메시지는 이후 운영 조회와 재시도 원인 분석에 사용된다.
     */
    public static NotificationAttempt failure(
            NotificationDispatch notificationDispatch,
            int attemptNo,
            String failureMessage,
            LocalDateTime requestedAt,
            LocalDateTime respondedAt
    ) {
        return create(notificationDispatch, attemptNo, NotificationAttemptResultStatus.FAILURE, failureMessage, requestedAt, respondedAt);
    }

    /**
     * timeout으로 결과를 확정하지 못한 시도 이력을 생성한다.
     * 이 경우 dispatch는 보통 UNKNOWN으로 이어져 자동 재시도 대신 운영 확인 대상이 된다.
     */
    public static NotificationAttempt timeout(
            NotificationDispatch notificationDispatch,
            int attemptNo,
            String failureMessage,
            LocalDateTime requestedAt,
            LocalDateTime respondedAt
    ) {
        return create(notificationDispatch, attemptNo, NotificationAttemptResultStatus.TIMEOUT, failureMessage, requestedAt, respondedAt);
    }

    /**
     * 시도 결과 종류와 무관하게 공통 이력 데이터를 채운다.
     * attempt는 생성 후 수정하지 않는 append-only 레코드로 사용한다.
     */
    private static NotificationAttempt create(
            NotificationDispatch notificationDispatch,
            int attemptNo,
            NotificationAttemptResultStatus resultStatus,
            String failureMessage,
            LocalDateTime requestedAt,
            LocalDateTime respondedAt
    ) {
        NotificationAttempt notificationAttempt = new NotificationAttempt();
        notificationAttempt.notificationDispatch = Objects.requireNonNull(notificationDispatch);
        notificationAttempt.attemptNo = attemptNo;
        notificationAttempt.resultStatus = Objects.requireNonNull(resultStatus);
        notificationAttempt.failureMessage = failureMessage;
        notificationAttempt.requestedAt = Objects.requireNonNull(requestedAt);
        notificationAttempt.respondedAt = Objects.requireNonNull(respondedAt);
        notificationAttempt.initializeCreatedAtAndUpdatedAt(requestedAt);
        return notificationAttempt;
    }
}

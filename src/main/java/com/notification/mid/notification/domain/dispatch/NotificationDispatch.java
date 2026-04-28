package com.notification.mid.notification.domain.dispatch;

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
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import com.notification.mid.notification.domain.notification.Notification;
import com.notification.mid.notification.domain.shared.BaseEntity;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.id.uuid.UuidVersion7Strategy;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "notification_dispatch",
        indexes = {
                @Index(
                        name = "idx_notification_dispatch_queue",
                        columnList = "status, next_retry_at, created_at, id"
                ),
                @Index(
                        name = "idx_notification_dispatch_recovery",
                        columnList = "status, updated_at, id"
                )
        },
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_notification_dispatch_notification_channel",
                        columnNames = {"notification_id", "channel"}
                )
        }
)
public class NotificationDispatch extends BaseEntity {

    @Id
    @GeneratedValue
    @UuidGenerator(algorithm = UuidVersion7Strategy.class)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "notification_id", nullable = false)
    private Notification notification;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 30)
    private NotificationChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private NotificationDispatchStatus status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    @Column(name = "send_started_at")
    private LocalDateTime sendStartedAt;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    /**
     * 채널별 발송 대기 row를 생성한다.
     * 알림 등록 직후에는 실제 발송 전이므로 항상 PENDING 상태에서 시작한다.
     */
    public static NotificationDispatch create(
            Notification notification,
            NotificationChannel channel,
            LocalDateTime now
    ) {
        NotificationDispatch notificationDispatch = new NotificationDispatch();
        notificationDispatch.notification = Objects.requireNonNull(notification);
        notificationDispatch.channel = Objects.requireNonNull(channel);
        notificationDispatch.status = NotificationDispatchStatus.PENDING;
        notificationDispatch.retryCount = 0;
        notificationDispatch.initializeCreatedAtAndUpdatedAt(now);
        return notificationDispatch;
    }

    /**
     * worker가 이 dispatch를 선점했음을 표시한다.
     * 이 시점부터 실제 외부 발송을 시작할 수 있으며, 이전 재시도 정보는 초기화된다.
     */
    public void markProcessing(LocalDateTime now) {
        changeStatus(NotificationDispatchStatus.PROCESSING, now);
        this.nextRetryAt = null;
        this.lastError = null;
        this.sendStartedAt = null;
    }

    /**
     * 외부 채널 발송 호출 직전에 실제 발송 시작 시각을 남긴다.
     * recovery는 이 값을 보고 아직 발송을 시작하지 못한 PROCESSING과 이미 외부 호출에 들어간 PROCESSING을 구분한다.
     */
    public void markSendStarted(LocalDateTime now) {
        if (status != NotificationDispatchStatus.PROCESSING) {
            throw new IllegalStateException("PROCESSING 상태에서만 발송 시작 시각을 기록할 수 있습니다.");
        }

        this.sendStartedAt = now;
        updateUpdatedAt(now);
    }

    /**
     * 발송이 성공적으로 끝났음을 반영한다.
     * 성공한 dispatch는 종결 상태가 되며 더 이상 자동 재시도 대상이 아니다.
     */
    public void markSuccess(LocalDateTime now) {
        changeStatus(NotificationDispatchStatus.SUCCESS, now);
        this.nextRetryAt = null;
        this.lastError = null;
        this.sendStartedAt = null;
    }

    /**
     * 재시도 가능한 실패를 기록하고 다음 재시도 시각까지 대기 상태로 전환한다.
     * retryCount, nextRetryAt, lastError는 이후 worker와 운영 조회가 참고하는 핵심 정보다.
     */
    public void markRetryWait(int retryCount, LocalDateTime nextRetryAt, String lastError, LocalDateTime now) {
        changeStatus(NotificationDispatchStatus.RETRY_WAIT, now);
        this.retryCount = retryCount;
        this.nextRetryAt = Objects.requireNonNull(nextRetryAt);
        this.lastError = lastError;
        this.sendStartedAt = null;
    }

    /**
     * 발송 결과를 확정할 수 없을 때 UNKNOWN으로 전환한다.
     * timeout이나 stale PROCESSING 복구처럼 중복 발송 위험 때문에 자동 재시도하지 않는 경우에 사용한다.
     */
    public void markUnknown(String lastError, LocalDateTime now) {
        changeStatus(NotificationDispatchStatus.UNKNOWN, now);
        this.nextRetryAt = null;
        this.lastError = lastError;
        this.sendStartedAt = null;
    }

    /**
     * 재시도 한도를 모두 소진한 최종 실패 상태로 종료한다.
     * 이후에는 자동 재시도하지 않고 운영 확인 또는 수동 개입 대상이 된다.
     */
    public void markFailed(int retryCount, String lastError, LocalDateTime now) {
        changeStatus(NotificationDispatchStatus.FAILED, now);
        this.retryCount = retryCount;
        this.nextRetryAt = null;
        this.lastError = lastError;
        this.sendStartedAt = null;
    }

    /**
     * IN_APP 알림을 사용자가 읽었음을 기록한다.
     * EMAIL처럼 읽음 여부를 추적하지 않는 채널은 이 메서드를 호출할 수 없다.
     */
    public void markRead(LocalDateTime now) {
        if (channel != NotificationChannel.IN_APP) {
            throw new IllegalStateException("IN_APP 채널만 읽음 처리할 수 있습니다.");
        }

        this.readAt = now;
        updateUpdatedAt(now);
    }

    /**
     * 현재 채널 특성과 readAt 값을 바탕으로 조회용 읽음 상태를 계산한다.
     * IN_APP이 아닌 채널은 읽음 의미가 없으므로 UNKNOWN으로 해석한다.
     */
    public NotificationReadStatus getReadStatus() {
        return NotificationReadStatus.from(channel, readAt);
    }

    /**
     * 허용된 상태 전이만 반영하도록 보호한다.
     * worker와 recovery가 상태를 바꿀 때 모두 같은 전이 규칙을 공유한다.
     */
    private void changeStatus(NotificationDispatchStatus nextStatus, LocalDateTime now) {
        status.assertCanChangeTo(nextStatus);
        this.status = nextStatus;
        updateUpdatedAt(now);
    }
}

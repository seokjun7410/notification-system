package com.notification.mid.notification.domain.notification;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import com.notification.mid.notification.domain.shared.BaseEntity;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.id.uuid.UuidVersion7Strategy;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "notification",
        indexes = {
                @Index(
                        name = "idx_notification_recipient_created_id",
                        columnList = "recipient_id, created_at desc, id desc"
                )
        },
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_notification_event_recipient_type",
                        columnNames = {"event_id", "recipient_id", "type"}
                )
        }
)
public class Notification extends BaseEntity {

    @Id
    @GeneratedValue
    @UuidGenerator(algorithm = UuidVersion7Strategy.class)
    private UUID id;

    @Column(name = "event_id", nullable = false, length = 150)
    private String eventId;

    @Column(name = "recipient_id", nullable = false, length = 100)
    private String recipientId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 50)
    private NotificationType type;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    /**
     * 비즈니스 이벤트 기준의 알림 원본을 생성한다.
     * 이후 채널별 발송 상태는 별도의 NotificationDispatch가 이어서 관리한다.
     */
    public static Notification create(
            String eventId,
            String recipientId,
            NotificationType type,
            String title,
            String content,
            LocalDateTime now
    ) {
        Notification notification = new Notification();
        notification.eventId = eventId;
        notification.recipientId = recipientId;
        notification.type = type;
        notification.title = title;
        notification.content = content;
        notification.initializeCreatedAtAndUpdatedAt(now);
        return notification;
    }

    /**
     * 같은 멱등성 키로 들어온 재요청이 기존 알림과 동일한 본문인지 확인한다.
     * 동일하면 같은 요청의 재시도로 보고, 다르면 충돌 요청으로 해석할 수 있다.
     */
    public boolean hasSamePayload(String title, String content) {
        return this.title.equals(title) && this.content.equals(content);
    }
}

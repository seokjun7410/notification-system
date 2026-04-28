package com.notification.mid.notification.support.fixture;

import java.time.LocalDateTime;
import java.util.Objects;

import com.notification.mid.notification.domain.notification.Notification;
import com.notification.mid.notification.domain.notification.NotificationType;

public final class NotificationFixture {

    private static final String DEFAULT_EVENT_ID = "event-1";
    private static final String DEFAULT_RECIPIENT_ID = "user-1";
    private static final NotificationType DEFAULT_TYPE = NotificationType.ORDER;
    private static final String DEFAULT_TITLE = "title";
    private static final String DEFAULT_CONTENT = "content";

    private NotificationFixture() {
    }

    public static Notification createNotification(LocalDateTime createdAt) {
        return builder(createdAt).build();
    }

    public static Notification createNotification(String eventId, LocalDateTime createdAt) {
        return builder(createdAt)
                .eventId(eventId)
                .build();
    }

    public static Notification createNotification(
            String eventId,
            String recipientId,
            NotificationType type,
            String title,
            String content,
            LocalDateTime createdAt
    ) {
        return builder(createdAt)
                .eventId(eventId)
                .recipientId(recipientId)
                .type(type)
                .title(title)
                .content(content)
                .build();
    }

    public static Builder builder(LocalDateTime createdAt) {
        return new Builder(createdAt);
    }

    public static final class Builder {

        private String eventId = DEFAULT_EVENT_ID;
        private String recipientId = DEFAULT_RECIPIENT_ID;
        private NotificationType type = DEFAULT_TYPE;
        private String title = DEFAULT_TITLE;
        private String content = DEFAULT_CONTENT;
        private final LocalDateTime createdAt;

        private Builder(LocalDateTime createdAt) {
            this.createdAt = Objects.requireNonNull(createdAt);
        }

        public Builder eventId(String eventId) {
            this.eventId = eventId;
            return this;
        }

        public Builder recipientId(String recipientId) {
            this.recipientId = recipientId;
            return this;
        }

        public Builder type(NotificationType type) {
            this.type = type;
            return this;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder content(String content) {
            this.content = content;
            return this;
        }

        public Notification build() {
            return Notification.create(eventId, recipientId, type, title, content, createdAt);
        }
    }
}

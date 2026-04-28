package com.notification.mid.notification.presentation.dto.request;

import com.notification.mid.notification.domain.dispatch.NotificationChannel;
import com.notification.mid.notification.domain.notification.NotificationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record NotificationCreateReq(
        @NotBlank(message = "eventId는 필수입니다.")
        @Size(max = 150, message = "eventId는 150자 이하여야 합니다.")
        String eventId,

        @NotBlank(message = "recipientId는 필수입니다.")
        @Size(max = 100, message = "recipientId는 100자 이하여야 합니다.")
        String recipientId,

        @NotNull(message = "type은 필수입니다.")
        NotificationType type,

        @NotNull(message = "channel은 필수입니다.")
        NotificationChannel channel,

        @NotBlank(message = "title은 필수입니다.")
        @Size(max = 200, message = "title은 200자 이하여야 합니다.")
        String title,

        @NotBlank(message = "content는 필수입니다.")
        @Size(max = 1000, message = "content는 1000자 이하여야 합니다.")
        String content
) {
}

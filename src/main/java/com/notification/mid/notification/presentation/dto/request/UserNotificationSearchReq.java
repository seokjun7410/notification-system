package com.notification.mid.notification.presentation.dto.request;

import com.notification.mid.notification.domain.dispatch.NotificationReadStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserNotificationSearchReq {

    // 읽음 상태
    private NotificationReadStatus readStatus;

    @Min(value = 1, message = "size는 1 이상이어야 합니다.")
    @Max(value = 100, message = "size는 100 이하여야 합니다.")
    private int size = 20;

    // 다음 페이지 시작점
    private String cursor;
}

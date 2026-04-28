package com.notification.mid.notification.presentation.controller;

import com.notification.mid.common.response.ApiRes;
import com.notification.mid.notification.application.dto.response.NotificationCreateRes;
import com.notification.mid.notification.application.dto.response.NotificationDetailRes;
import com.notification.mid.notification.application.dto.response.UserNotificationPageRes;
import com.notification.mid.notification.application.port.in.NotificationQueryService;
import com.notification.mid.notification.application.port.in.NotificationRegistrationService;
import com.notification.mid.notification.presentation.dto.request.NotificationCreateReq;
import com.notification.mid.notification.presentation.dto.request.UserNotificationSearchReq;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1")
public class NotificationWebController {

    private final NotificationRegistrationService notificationRegistrationService;
    private final NotificationQueryService notificationQueryService;

    @PostMapping("/notifications")
    public ResponseEntity<ApiRes<NotificationCreateRes>> create(@Valid @RequestBody NotificationCreateReq req) {
        NotificationCreateRes response = notificationRegistrationService.register(
                req.eventId(),
                req.recipientId(),
                req.type(),
                req.channel(),
                req.title(),
                req.content()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiRes.success(response));
    }

    @GetMapping("/notifications/{notificationId}")
    public ResponseEntity<ApiRes<NotificationDetailRes>> get(@PathVariable UUID notificationId) {
        NotificationDetailRes response = notificationQueryService.getNotification(notificationId);

        return ResponseEntity.ok(ApiRes.success(response));
    }

    @GetMapping("/users/{recipientId}/notifications")
    public ResponseEntity<ApiRes<UserNotificationPageRes>> searchUserInAppNotifications(
            @PathVariable String recipientId,
            @Valid @ModelAttribute UserNotificationSearchReq req
    ) {
        UserNotificationPageRes response = notificationQueryService.searchUserInApp(
                recipientId,
                req.getReadStatus(),
                req.getSize(),
                req.getCursor()
        );

        return ResponseEntity.ok(ApiRes.success(response));
    }
}

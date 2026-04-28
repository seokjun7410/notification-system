package com.notification.mid.notification.presentation.controller;

import com.notification.mid.common.response.ApiRes;
import com.notification.mid.notification.application.dto.response.NotificationPageRes;
import com.notification.mid.notification.application.port.in.NotificationQueryService;
import com.notification.mid.notification.domain.dispatch.NotificationChannel;
import com.notification.mid.notification.domain.dispatch.NotificationReadStatus;
import com.notification.mid.notification.presentation.dto.request.NotificationSearchReq;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/admin")
public class AdminNotificationWebController {

    private final NotificationQueryService notificationQueryService;

    @GetMapping("/users/{recipientId}/notifications")
    public ResponseEntity<ApiRes<NotificationPageRes>> searchNotifications(
            @PathVariable String recipientId,
            @Valid @ModelAttribute NotificationSearchReq req
    ) {
        NotificationChannel channel = req.getChannel();
        NotificationReadStatus readStatus = req.getReadStatus();
        NotificationPageRes response = notificationQueryService.searchAdmin(
                recipientId,
                channel,
                readStatus,
                req.getSize(),
                req.getCursor()
        );

        return ResponseEntity.ok(ApiRes.success(response));
    }
}

package com.notification.mid.notification.domain.notification;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NotificationType {

    ORDER("주문"),
    PAYMENT("결제"),
    GENERAL("일반");

    private final String description;
}

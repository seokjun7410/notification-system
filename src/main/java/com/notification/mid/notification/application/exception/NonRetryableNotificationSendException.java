package com.notification.mid.notification.application.exception;

public class NonRetryableNotificationSendException extends RuntimeException {

    public NonRetryableNotificationSendException(String message) {
        super(message);
    }
}

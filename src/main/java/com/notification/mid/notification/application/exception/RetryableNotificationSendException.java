package com.notification.mid.notification.application.exception;

public class RetryableNotificationSendException extends RuntimeException {

    public RetryableNotificationSendException(String message) {
        super(message);
    }
}

package com.notification.mid.common.exception;

import com.notification.mid.common.exception.shared.BusinessException;
import com.notification.mid.common.exception.shared.ErrorCode;

public class DuplicateNotificationRequestException extends BusinessException {

    public DuplicateNotificationRequestException() {
        super(ErrorCode.DUPLICATE_NOTIFICATION_REQUEST);
    }
}

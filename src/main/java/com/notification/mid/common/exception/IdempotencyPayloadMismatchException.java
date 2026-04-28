package com.notification.mid.common.exception;

import com.notification.mid.common.exception.shared.BusinessException;
import com.notification.mid.common.exception.shared.ErrorCode;

public class IdempotencyPayloadMismatchException extends BusinessException {

    public IdempotencyPayloadMismatchException() {
        super(ErrorCode.IDEMPOTENCY_PAYLOAD_MISMATCH);
    }
}

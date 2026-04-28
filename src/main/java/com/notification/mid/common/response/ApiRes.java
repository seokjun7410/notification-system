package com.notification.mid.common.response;

import com.notification.mid.common.exception.shared.ErrorCode;

public record ApiRes<T>(
        boolean success,
        String code,
        String message,
        T data
) {

    public static <T> ApiRes<T> success(T data) {
        return new ApiRes<>(true, "SUCCESS", "OK", data);
    }

    public static <T> ApiRes<T> success(String code, String message, T data) {
        return new ApiRes<>(true, code, message, data);
    }

    public static ApiRes<Void> error(ErrorCode errorCode) {
        return new ApiRes<>(false, errorCode.getCode(), errorCode.getMessage(), null);
    }

    public static ApiRes<Void> error(ErrorCode errorCode, String message) {
        return new ApiRes<>(false, errorCode.getCode(), message, null);
    }
}

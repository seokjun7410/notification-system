package com.notification.mid.common.exception.shared;

import com.notification.mid.common.response.ApiRes;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.convert.ConversionFailedException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import jakarta.validation.ConstraintViolationException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiRes<Void>> handleBusinessException(BusinessException exception) {
        ErrorCode errorCode = exception.getErrorCode();
        log.warn("비즈니스 예외가 발생했습니다. code={}, message={}", errorCode.getCode(), exception.getMessage());

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ApiRes.error(errorCode, exception.getMessage()));
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            BindException.class,
            ConstraintViolationException.class,
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            ConversionFailedException.class
    })
    public ResponseEntity<ApiRes<Void>> handleValidationException(Exception exception) {
        String message = extractValidationMessage(exception);
        log.warn("검증 예외가 발생했습니다. message={}", message);

        return ResponseEntity
                .badRequest()
                .body(ApiRes.error(ErrorCode.INVALID_REQUEST, message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiRes<Void>> handleException(Exception exception) {
        log.error("처리되지 않은 예외가 발생했습니다.", exception);
        exception.printStackTrace();

        return ResponseEntity
                .status(ErrorCode.INTERNAL_ERROR.getStatus())
                .body(ApiRes.error(ErrorCode.INTERNAL_ERROR));
    }

    private String extractValidationMessage(Exception exception) {
        if (exception instanceof MethodArgumentNotValidException methodArgumentNotValidException) {
            return methodArgumentNotValidException.getBindingResult()
                    .getFieldErrors()
                    .stream()
                    .map(fieldError -> formatFieldValidationMessage(fieldError.getField(), fieldError.getDefaultMessage()))
                    .findFirst()
                    .orElse(ErrorCode.INVALID_REQUEST.getMessage());
        }

        if (exception instanceof BindException bindException) {
            return bindException.getBindingResult()
                    .getFieldErrors()
                    .stream()
                    .map(fieldError -> formatFieldValidationMessage(fieldError.getField(), fieldError.getDefaultMessage()))
                    .findFirst()
                    .orElse(ErrorCode.INVALID_REQUEST.getMessage());
        }

        if (exception instanceof ConstraintViolationException constraintViolationException) {
            return constraintViolationException.getConstraintViolations()
                    .stream()
                    .map(violation -> violation.getPropertyPath() + " " + violation.getMessage())
                    .findFirst()
                    .orElse(ErrorCode.INVALID_REQUEST.getMessage());
        }

        if (exception instanceof HttpMessageNotReadableException httpMessageNotReadableException) {
            return "요청 본문 형식이 올바르지 않습니다.";
        }

        if (exception instanceof MethodArgumentTypeMismatchException methodArgumentTypeMismatchException) {
            return methodArgumentTypeMismatchException.getName() + " 형식이 올바르지 않습니다.";
        }

        if (exception instanceof ConversionFailedException conversionFailedException) {
            return "요청 파라미터 형식이 올바르지 않습니다.";
        }

        return ErrorCode.INVALID_REQUEST.getMessage();
    }

    private String formatFieldValidationMessage(String field, String defaultMessage) {
        if (defaultMessage == null || defaultMessage.isBlank()) {
            return field;
        }

        return defaultMessage.startsWith(field)
                ? defaultMessage
                : field + " " + defaultMessage;
    }
}

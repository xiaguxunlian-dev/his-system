package com.his.shared.exception;

/**
 * 数据校验异常
 */
public class ValidationException extends AppException {
    public ValidationException(String message) {
        super(message, "VALIDATION_ERROR", 422);
    }
}

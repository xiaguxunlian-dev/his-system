package com.his.shared.exception;

/**
 * 业务逻辑异常
 */
public class BusinessException extends AppException {
    public BusinessException(String message) {
        super(message, "BUSINESS_ERROR", 400);
    }

    public BusinessException(String message, String code) {
        super(message, code, 400);
    }
}

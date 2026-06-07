package com.his.shared.exception;

/**
 * 资源未找到异常
 */
public class NotFoundException extends AppException {
    public NotFoundException(String resource, Object id) {
        super(String.format("%s 未找到: %s", resource, id), "NOT_FOUND", 404);
    }

    public NotFoundException(String message) {
        super(message, "NOT_FOUND", 404);
    }
}

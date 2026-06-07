package com.his.shared.exception;

/**
 * 应用基础异常类
 * 所有业务异常应继承此类
 */
public class AppException extends RuntimeException {

    private final String code;
    private final int statusCode;
    private final boolean operational;

    public AppException(String message) {
        this(message, "APP_ERROR", 500, true);
    }

    public AppException(String message, String code, int statusCode) {
        this(message, code, statusCode, true);
    }

    public AppException(String message, String code, int statusCode, boolean operational) {
        super(message);
        this.code = code;
        this.statusCode = statusCode;
        this.operational = operational;
    }

    public String getCode() { return code; }
    public int getStatusCode() { return statusCode; }
    public boolean isOperational() { return operational; }

    @Override
    public String toString() {
        return String.format("[%s] %s (HTTP %d)", code, getMessage(), statusCode);
    }
}

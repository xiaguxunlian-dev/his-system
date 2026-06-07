package com.his.shared.exception;

/**
 * 数据库异常
 */
public class DatabaseException extends AppException {
    public DatabaseException(String message) {
        super(message, "DATABASE_ERROR", 500, false);
    }

    public DatabaseException(String message, Throwable cause) {
        super(message + (cause != null ? ": " + cause.getMessage() : ""),
              "DATABASE_ERROR", 500, false);
    }
}

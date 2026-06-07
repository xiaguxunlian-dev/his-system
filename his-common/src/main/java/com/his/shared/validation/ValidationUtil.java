package com.his.shared.validation;

import com.his.shared.exception.ValidationException;

/**
 * 输入验证工具类
 */
public final class ValidationUtil {

    private ValidationUtil() {}

    /**
     * 验证字符串非空
     */
    public static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new ValidationException(fieldName + " 不能为空");
        }
        return value.trim();
    }

    /**
     * 验证字符串最大长度
     */
    public static String requireMaxLength(String value, int maxLength, String fieldName) {
        if (value != null && value.length() > maxLength) {
            throw new ValidationException(fieldName + " 长度不能超过 " + maxLength + " 个字符");
        }
        return value;
    }

    /**
     * 验证正整数
     */
    public static int requirePositive(int value, String fieldName) {
        if (value <= 0) {
            throw new ValidationException(fieldName + " 必须大于 0");
        }
        return value;
    }

    /**
     * 验证非负数
     */
    public static int requireNonNegative(int value, String fieldName) {
        if (value < 0) {
            throw new ValidationException(fieldName + " 不能为负数");
        }
        return value;
    }

    /**
     * 验证非空对象
     */
    public static <T> T requireNotNull(T obj, String fieldName) {
        if (obj == null) {
            throw new ValidationException(fieldName + " 不能为空");
        }
        return obj;
    }
}

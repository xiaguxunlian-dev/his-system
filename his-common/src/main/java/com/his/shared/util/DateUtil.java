package com.his.shared.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 日期工具类
 */
public final class DateUtil {

    private DateUtil() {}

    public static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    public static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static String formatDate(LocalDate date) {
        return date != null ? date.format(DATE_FORMAT) : "";
    }

    public static String formatDateTime(LocalDateTime dt) {
        return dt != null ? dt.format(DATETIME_FORMAT) : "";
    }

    public static LocalDate parseDate(String str) {
        return str != null && !str.isEmpty() ? LocalDate.parse(str, DATE_FORMAT) : null;
    }

    public static String now() {
        return formatDate(LocalDate.now());
    }
}

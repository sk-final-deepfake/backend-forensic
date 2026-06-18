package com.example.demo.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class ApiDateTimeFormatter {

    private static final DateTimeFormatter UTC = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");

    private ApiDateTimeFormatter() {
    }

    public static String formatUtc(LocalDateTime value) {
        return value == null ? null : UTC.format(value);
    }
}

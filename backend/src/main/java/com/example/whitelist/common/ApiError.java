package com.example.whitelist.common;

import java.time.LocalDateTime;
import java.util.Map;

public record ApiError(int status, String message, Map<String, String> fields, LocalDateTime timestamp) {
    public static ApiError of(int status, String message) {
        return new ApiError(status, message, null, LocalDateTime.now());
    }
}

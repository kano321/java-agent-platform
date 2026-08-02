package com.agentplatform.common.api;

import java.time.Instant;

/**
 * Unified API response wrapper.
 *
 * @param code      business code, 0 means success
 * @param message   human readable message
 * @param data      payload
 * @param timestamp response time
 * @param <T>       payload type
 */
public record ApiResponse<T>(int code, String message, T data, Instant timestamp) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(0, "success", data, Instant.now());
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(0, message, data, Instant.now());
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, message, null, Instant.now());
    }
}

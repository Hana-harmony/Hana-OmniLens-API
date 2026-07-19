package com.hana.omniconnect.common.api;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean success,
        int status,
        String code,
        String message,
        T data,
        List<FieldErrorDetail> errors,
        Instant timestamp
) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, 200, "COMMON_000", "OK", data, null, Instant.now());
    }

    public static <T> ApiResponse<T> error(int status, String code, String message) {
        return new ApiResponse<>(false, status, code, message, null, null, Instant.now());
    }

    public static <T> ApiResponse<T> error(
            int status,
            String code,
            String message,
            List<FieldErrorDetail> errors
    ) {
        return new ApiResponse<>(false, status, code, message, null, errors, Instant.now());
    }
}

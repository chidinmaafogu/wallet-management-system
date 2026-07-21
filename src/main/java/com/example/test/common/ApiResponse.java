package com.example.test.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.slf4j.MDC;

import java.time.Instant;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean success,
        String code,
        String message,
        T data,
        Map<String, String> errors,
        Instant timestamp,
        String correlationId
) {

    public static <T> ApiResponse<T> ok(T data, String message) {
        return new ApiResponse<>(true, "SUCCESS", message, data, null, Instant.now(), currentCorrelationId());
    }

    public static <T> ApiResponse<T> failure(String code, String message) {
        return new ApiResponse<>(false, code, message, null, null, Instant.now(), currentCorrelationId());
    }

    public static <T> ApiResponse<T> failure(String code, String message, Map<String, String> errors) {
        return new ApiResponse<>(false, code, message, null, errors, Instant.now(), currentCorrelationId());
    }

    private static String currentCorrelationId() {
        return MDC.get(CorrelationIdFilter.CORRELATION_ID);
    }
}

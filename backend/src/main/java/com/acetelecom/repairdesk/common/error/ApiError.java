package com.acetelecom.repairdesk.common.error;

import java.time.Instant;
import java.util.List;

/**
 * Standard error response shape (Architecture §Error Handling, API.md §18).
 * No stack traces, SQL, or sensitive data are ever included.
 */
public record ApiError(
        String errorCode,
        String message,
        String correlationId,
        String path,
        Instant timestamp,
        List<FieldError> fieldErrors) {

    public record FieldError(String field, String message) {
    }

    public static ApiError of(ErrorCode code, String message, String correlationId, String path,
            List<FieldError> fieldErrors) {
        return new ApiError(code.name(), message, correlationId, path, Instant.now(),
                fieldErrors == null ? List.of() : fieldErrors);
    }
}

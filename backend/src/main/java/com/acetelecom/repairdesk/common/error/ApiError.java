package com.acetelecom.repairdesk.common.error;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Standard error response shape (Architecture §Error Handling, API.md §18).
 * No stack traces, SQL, or sensitive data are ever included.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiError(
        String errorCode,
        String message,
        String correlationId,
        String path,
        Instant timestamp,
        List<FieldError> fieldErrors,
        Map<String, Object> details) {

    public record FieldError(String field, String message) {
    }

    public static ApiError of(ErrorCode code, String message, String correlationId, String path,
            List<FieldError> fieldErrors) {
        return of(code, message, correlationId, path, fieldErrors, Map.of());
    }

    public static ApiError of(ErrorCode code, String message, String correlationId, String path,
            List<FieldError> fieldErrors, Map<String, Object> details) {
        return new ApiError(code.name(), message, correlationId, path, Instant.now(),
                fieldErrors == null ? List.of() : fieldErrors,
                details == null ? Map.of() : details);
    }
}

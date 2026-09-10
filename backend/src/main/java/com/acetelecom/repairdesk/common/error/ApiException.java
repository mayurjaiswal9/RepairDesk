package com.acetelecom.repairdesk.common.error;

import java.util.Map;

/**
 * Base application exception carrying a stable {@link ErrorCode}. Services throw
 * subclasses/instances of this; the {@link GlobalExceptionHandler} maps them to
 * the standard {@link ApiError} response.
 */
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Map<String, Object> details;

    public ApiException(ErrorCode errorCode, String message) {
        this(errorCode, message, Map.of());
    }

    public ApiException(ErrorCode errorCode, String message, Map<String, Object> details) {
        super(message);
        this.errorCode = errorCode;
        this.details = details == null ? Map.of() : Map.copyOf(details);
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public Map<String, Object> getDetails() {
        return details;
    }
}

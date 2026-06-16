package com.acetelecom.repairdesk.common.error;

/**
 * Base application exception carrying a stable {@link ErrorCode}. Services throw
 * subclasses/instances of this; the {@link GlobalExceptionHandler} maps them to
 * the standard {@link ApiError} response.
 */
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;

    public ApiException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}

package com.acetelecom.repairdesk.common.error;

import org.springframework.http.HttpStatus;

/**
 * Canonical application error codes mapped to HTTP statuses. Returned in the
 * standard {@link ApiError} response so clients can branch on a stable code.
 */
public enum ErrorCode {

    VALIDATION_ERROR(HttpStatus.UNPROCESSABLE_ENTITY),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED),
    FORBIDDEN(HttpStatus.FORBIDDEN),
    NOT_FOUND(HttpStatus.NOT_FOUND),
    CONFLICT(HttpStatus.CONFLICT),
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}

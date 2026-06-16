package com.acetelecom.repairdesk.common.error;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Translates exceptions into the standard {@link ApiError} response. Establishes
 * the pattern extended in CORE-001; feature-specific exceptions extend
 * {@link ApiException}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String CORRELATION_ID_KEY = "correlationId";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        List<ApiError.FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ApiError.FieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();
        return build(ErrorCode.VALIDATION_ERROR, "Validation failed", fieldErrors);
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApiException(ApiException ex) {
        return build(ex.getErrorCode(), ex.getMessage(), List.of());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex) {
        return build(ErrorCode.VALIDATION_ERROR, "Malformed or unreadable request body", List.of());
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        return build(ErrorCode.CONFLICT,
                "The record was modified by another request. Please reload and try again.", List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex) {
        // Log the detail server-side; never leak internals to the client.
        log.error("Unhandled exception", ex);
        return build(ErrorCode.INTERNAL_ERROR, "An unexpected error occurred", List.of());
    }

    private ResponseEntity<ApiError> build(ErrorCode code, String message, List<ApiError.FieldError> fieldErrors) {
        ApiError body = ApiError.of(code, message, MDC.get(CORRELATION_ID_KEY), currentPath(), fieldErrors);
        return ResponseEntity.status(code.status()).body(body);
    }

    private String currentPath() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes servletAttrs) {
            HttpServletRequest request = servletAttrs.getRequest();
            return request.getRequestURI();
        }
        return null;
    }
}

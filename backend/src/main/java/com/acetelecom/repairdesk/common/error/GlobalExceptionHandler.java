package com.acetelecom.repairdesk.common.error;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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
        return build(ex.getErrorCode(), ex.getMessage(), List.of(), ex.getDetails());
    }

    /**
     * {@code @Secured} denials are thrown inside the controller, so they reach
     * this advice before the security filter can turn them into 403. Map them
     * explicitly; the catch-all below would otherwise report them as 500.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex) {
        return build(ErrorCode.FORBIDDEN, "Access denied", List.of());
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

    /**
     * BUG-10: without this, a request that hits a known path with the wrong
     * HTTP verb (e.g. {@code DELETE} on an endpoint that only supports
     * {@code POST .../archive}) fell through to {@link #handleGeneric}, which
     * logged it as an "unhandled exception" and returned a misleading 500
     * instead of the correct 405.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return build(ErrorCode.METHOD_NOT_ALLOWED, ex.getMessage(), List.of());
    }

    /** BUG-10: same reasoning as {@link #handleMethodNotSupported} but for unknown paths. */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoResourceFound(NoResourceFoundException ex) {
        return build(ErrorCode.NOT_FOUND, "No endpoint found for this request", List.of());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleUploadTooLarge(MaxUploadSizeExceededException ex) {
        return build(ErrorCode.VALIDATION_ERROR,
                "Photo is too large. Maximum size is 10 MB.", List.of());
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiError> handleMultipart(MultipartException ex) {
        log.warn("Multipart request failed: {}", ex.getMessage());
        return build(ErrorCode.VALIDATION_ERROR,
                "Could not read the photo. Please try taking or choosing it again.", List.of());
    }

    @ExceptionHandler({
            MissingServletRequestPartException.class,
            MissingServletRequestParameterException.class
    })
    public ResponseEntity<ApiError> handleMissingUploadPart(Exception ex) {
        log.warn("Upload part missing: {}", ex.getMessage());
        return build(ErrorCode.VALIDATION_ERROR, "A photo file is required.", List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex) {
        // Log the detail server-side; never leak internals to the client.
        log.error("Unhandled exception", ex);
        return build(ErrorCode.INTERNAL_ERROR, "An unexpected error occurred", List.of());
    }

    private ResponseEntity<ApiError> build(ErrorCode code, String message, List<ApiError.FieldError> fieldErrors) {
        return build(code, message, fieldErrors, java.util.Map.of());
    }

    private ResponseEntity<ApiError> build(ErrorCode code, String message,
            List<ApiError.FieldError> fieldErrors, java.util.Map<String, Object> details) {
        ApiError body = ApiError.of(code, message, MDC.get(CORRELATION_ID_KEY), currentPath(),
                fieldErrors, details);
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

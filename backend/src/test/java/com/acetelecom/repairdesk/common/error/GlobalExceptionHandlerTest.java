package com.acetelecom.repairdesk.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * BUG-10 regression: framework-level "wrong method" / "unknown route"
 * exceptions must surface as their proper HTTP status, not fall through to
 * the generic 500 handler.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void wrongHttpMethod_returns405NotAGeneric500() {
        var ex = new HttpRequestMethodNotSupportedException("DELETE", List.of("POST"));

        ResponseEntity<ApiError> response = handler.handleMethodNotSupported(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getBody().errorCode()).isEqualTo(ErrorCode.METHOD_NOT_ALLOWED.name());
    }

    @Test
    void unknownRoute_returns404NotAGeneric500() {
        var ex = new NoResourceFoundException(HttpMethod.GET, "/does-not-exist", "/does-not-exist");

        ResponseEntity<ApiError> response = handler.handleNoResourceFound(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().errorCode()).isEqualTo(ErrorCode.NOT_FOUND.name());
    }
}

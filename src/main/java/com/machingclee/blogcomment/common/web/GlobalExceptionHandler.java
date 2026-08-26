package com.machingclee.blogcomment.common.web;

import com.machingclee.blogcomment.common.dto.ApiResponse;
import com.machingclee.blogcomment.common.exception.BadRequestException;
import com.machingclee.blogcomment.common.exception.RateLimitExceededException;
import com.machingclee.blogcomment.common.exception.UnauthorizedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Single error-handling point: every error leaves the API as the
 * ApiResponse envelope with a real HTTP status, so the frontend's
 * `unwrap()` rejects non-2xx and rolls back its optimistic update.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static ResponseEntity<ApiResponse<Void>> failed(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .body(ApiResponse.failed(ApiResponse.FailedParam.<Void>builder().errorMessage(message).build()));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> onMissingParam(MissingServletRequestParameterException e) {
        return failed(HttpStatus.BAD_REQUEST, e.getParameterName() + " is required");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> onTypeMismatch(MethodArgumentTypeMismatchException e) {
        return failed(HttpStatus.BAD_REQUEST, "invalid " + e.getName());
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiResponse<Void>> onBadRequest(BadRequestException e) {
        return failed(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiResponse<Void>> onUnauthorized(UnauthorizedException e) {
        return failed(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ApiResponse<Void>> onRateLimit(RateLimitExceededException e) {
        return failed(HttpStatus.TOO_MANY_REQUESTS, e.getMessage());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> onNotFound(NoResourceFoundException e) {
        return failed(HttpStatus.NOT_FOUND, "not found");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> onUnexpected(Exception e) {
        log.error("Unexpected error", e);
        return failed(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
    }
}

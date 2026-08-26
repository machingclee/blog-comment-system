package com.machingclee.blogcomment.common.exception;

/**
 * Maps to HTTP 401 with the message in the ApiResponse envelope.
 */
public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String message) {
        super(message);
    }
}

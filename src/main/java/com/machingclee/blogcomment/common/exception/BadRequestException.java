package com.machingclee.blogcomment.common.exception;

/**
 * Maps to HTTP 400 with the message in the ApiResponse envelope.
 */
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
}

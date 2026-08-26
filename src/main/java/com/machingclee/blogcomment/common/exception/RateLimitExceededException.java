package com.machingclee.blogcomment.common.exception;

/**
 * Maps to HTTP 429 with the message in the ApiResponse envelope.
 */
public class RateLimitExceededException extends RuntimeException {
    public RateLimitExceededException(String message) {
        super(message);
    }
}

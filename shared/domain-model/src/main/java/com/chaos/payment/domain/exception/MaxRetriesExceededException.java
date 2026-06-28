package com.chaos.payment.domain.exception;

public class MaxRetriesExceededException extends RuntimeException {
    public MaxRetriesExceededException(String message) {
        super(message);
    }

    public MaxRetriesExceededException(String message, Throwable cause) {
        super(message, cause);
    }
}

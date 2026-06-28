package com.chaos.payment.infrastructure.chaos;

public class ChaosException extends RuntimeException {
    public ChaosException(String message) {
        super(message);
    }

    public ChaosException(String message, Throwable cause) {
        super(message, cause);
    }
}

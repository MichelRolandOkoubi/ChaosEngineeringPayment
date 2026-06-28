package com.chaos.payment.infrastructure.chaos;

public class ThrottleException extends ChaosException {
    public ThrottleException(String message) {
        super(message);
    }
}

package com.chaos.payment.infrastructure.chaos;

public class DuplicateTransactionException extends ChaosException {
    public DuplicateTransactionException(String message) {
        super(message);
    }
}

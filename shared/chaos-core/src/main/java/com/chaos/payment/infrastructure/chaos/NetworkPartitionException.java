package com.chaos.payment.infrastructure.chaos;

public class NetworkPartitionException extends ChaosException {
    public NetworkPartitionException(String message) {
        super(message);
    }
}

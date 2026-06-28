package com.chaos.payment.infrastructure.persistence;
public class EventStoreException extends RuntimeException {
    public EventStoreException(String message, Throwable cause) { super(message, cause); }
}
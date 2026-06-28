package com.chaos.payment.infrastructure.persistence;
public class ConcurrencyException extends RuntimeException {
    public ConcurrencyException(String message, Throwable cause) { super(message, cause); }
}
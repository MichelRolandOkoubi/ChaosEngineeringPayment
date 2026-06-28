package com.chaos.payment.interfaces.rest;
import lombok.Value;
@Value
public class HealthStatus {
    String status;
    long timestamp;
    String mode;
}
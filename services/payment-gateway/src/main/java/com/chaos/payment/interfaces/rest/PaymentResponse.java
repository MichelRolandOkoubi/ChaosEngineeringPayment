package com.chaos.payment.interfaces.rest;
import lombok.Value;
@Value
public class PaymentResponse {
    String transactionId;
    String status;
    String message;
}
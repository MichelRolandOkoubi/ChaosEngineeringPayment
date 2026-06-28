package com.chaos.payment.application.query;
import lombok.Value;
@Value
public class ListPaymentsQuery {
    String userId;
    String providerId;
    String status;
    int page;
    int size;
}
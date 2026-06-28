package com.chaos.payment.infrastructure.provider;

import lombok.Data;

@Data
public class MTNPaymentStatus {
    private String status;
    private String financialTransactionId;
    private String externalId;
    private String reason;
}

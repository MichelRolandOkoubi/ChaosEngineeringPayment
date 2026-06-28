package com.chaos.payment.infrastructure.provider;

import lombok.Data;

@Data
public class VisaPaymentResponse {
    private String responseCode;
    private String transactionIdentifier;
    private String responseMessage;
    private String authCode;
}

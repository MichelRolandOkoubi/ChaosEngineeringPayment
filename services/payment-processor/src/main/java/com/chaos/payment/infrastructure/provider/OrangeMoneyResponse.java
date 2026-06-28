package com.chaos.payment.infrastructure.provider;

import lombok.Data;

@Data
public class OrangeMoneyResponse {
    private boolean success;
    private String payToken;
    private String paymentUrl;
    private String status;
    private String message;
}

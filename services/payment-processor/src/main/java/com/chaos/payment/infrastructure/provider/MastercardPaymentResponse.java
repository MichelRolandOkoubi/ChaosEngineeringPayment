package com.chaos.payment.infrastructure.provider;

import lombok.Data;

@Data
public class MastercardPaymentResponse {
    private String result;
    private String sessionId;
    private String successIndicator;
    private String orderId;
}

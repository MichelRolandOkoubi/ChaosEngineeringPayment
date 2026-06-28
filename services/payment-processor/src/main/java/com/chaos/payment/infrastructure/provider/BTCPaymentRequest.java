package com.chaos.payment.infrastructure.provider;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BTCPaymentRequest {
    private double price;
    private String currency;
    private String orderId;
    private String redirectURL;
    private String notificationURL;
}

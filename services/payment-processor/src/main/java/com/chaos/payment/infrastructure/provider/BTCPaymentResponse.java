package com.chaos.payment.infrastructure.provider;

import lombok.Data;

@Data
public class BTCPaymentResponse {
    private String id;
    private String checkoutLink;
    private String status;
    private String currency;
    private double amount;
}

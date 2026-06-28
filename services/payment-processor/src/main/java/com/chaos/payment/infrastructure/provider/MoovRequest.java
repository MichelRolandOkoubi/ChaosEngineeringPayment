package com.chaos.payment.infrastructure.provider;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MoovRequest {
    private String amount;
    private String currency;
    private String phoneNumber;
    private String transactionId;
    private String description;
}

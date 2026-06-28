package com.chaos.payment.infrastructure.provider;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MTNPaymentRequest {
    private String amount;
    private String currency;
    private String externalId;
    private MTNPayer payer;
    private String payerMessage;
    private String payeeNote;
}

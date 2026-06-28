package com.chaos.payment.infrastructure.provider;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Builder
public class VisaPaymentRequest {
    private BigDecimal amount;
    private String currency;
    private VisaCardData paymentMethodData;
    private String transactionId;
}

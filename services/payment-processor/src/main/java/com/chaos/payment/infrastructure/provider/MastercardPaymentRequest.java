package com.chaos.payment.infrastructure.provider;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Builder
public class MastercardPaymentRequest {
    private BigDecimal amount;
    private String currency;
    private String orderId;
    private String description;
}

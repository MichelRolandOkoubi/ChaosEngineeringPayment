package com.chaos.payment.infrastructure.provider;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Builder
public class OrangeMoneyRequest {
    private String merchantId;
    private BigDecimal amount;
    private String currency;
    private String customerPhone;
    private String reference;
    private String callbackUrl;
    private String notifUrl;
    private String returnUrl;
}

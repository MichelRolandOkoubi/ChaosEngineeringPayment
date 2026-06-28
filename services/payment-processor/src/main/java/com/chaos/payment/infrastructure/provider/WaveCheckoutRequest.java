package com.chaos.payment.infrastructure.provider;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Builder
public class WaveCheckoutRequest {
    private BigDecimal amount;
    private String currency;
    private String clientReference;
    private String mobileNumber;
    private String webhookUrl;
}

package com.chaos.payment.infrastructure.provider;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AirtelPaymentRequest {
    private String reference;
    private AirtelSubscriber subscriber;
    private AirtelTransaction transaction;
}

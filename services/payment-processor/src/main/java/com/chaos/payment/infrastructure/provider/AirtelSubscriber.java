package com.chaos.payment.infrastructure.provider;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AirtelSubscriber {
    private String msisdn;
    private String country;
}

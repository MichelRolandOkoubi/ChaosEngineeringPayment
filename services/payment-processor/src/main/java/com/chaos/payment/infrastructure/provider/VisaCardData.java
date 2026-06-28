package com.chaos.payment.infrastructure.provider;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class VisaCardData {
    private String cardNumber;
    private String expiryMonth;
    private String expiryYear;
    private String cvv;
}

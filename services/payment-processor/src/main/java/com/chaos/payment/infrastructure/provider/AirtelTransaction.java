package com.chaos.payment.infrastructure.provider;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AirtelTransaction {
    private BigDecimal amount;
    private String currency;
    private String id;
}

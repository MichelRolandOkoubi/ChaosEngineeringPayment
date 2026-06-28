package com.chaos.payment.domain.valueobject;
import lombok.Value;
import java.math.BigDecimal;
@Value
public class Money {
    BigDecimal amount;
    Currency currency;
}
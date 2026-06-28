package com.chaos.payment.domain.valueobject;
import lombok.Value;
import java.util.UUID;
@Value
public class TransactionId {
    String value;
    public static TransactionId generate() {
        return new TransactionId(UUID.randomUUID().toString());
    }
}
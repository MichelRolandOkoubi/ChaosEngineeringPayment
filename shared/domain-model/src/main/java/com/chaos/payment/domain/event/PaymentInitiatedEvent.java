package com.chaos.payment.domain.event;
import lombok.Value;
import java.math.BigDecimal;
import java.time.Instant;
@Value
public class PaymentInitiatedEvent implements DomainEvent {
    String transactionId;
    String userId;
    BigDecimal amount;
    String currencyCode;
    String providerId;
    String description;
    Instant occurredAt;
    @Override public String getAggregateId() { return transactionId; }
    @Override public int getVersion() { return 1; }
    @Override public String getCorrelationId() { return null; }
}
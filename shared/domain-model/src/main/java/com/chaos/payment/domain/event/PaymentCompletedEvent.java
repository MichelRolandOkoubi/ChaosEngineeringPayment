package com.chaos.payment.domain.event;
import lombok.Value;
import java.math.BigDecimal;
import java.time.Instant;
@Value
public class PaymentCompletedEvent implements DomainEvent {
    String transactionId;
    String externalReference;
    BigDecimal amount;
    String currencyCode;
    String providerId;
    Instant occurredAt;
    @Override public String getAggregateId() { return transactionId; }
    @Override public int getVersion() { return 3; }
    @Override public String getCorrelationId() { return null; }
}
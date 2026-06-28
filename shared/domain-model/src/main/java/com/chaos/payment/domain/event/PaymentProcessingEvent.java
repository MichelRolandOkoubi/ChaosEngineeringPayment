package com.chaos.payment.domain.event;
import lombok.Value;
import java.time.Instant;
@Value
public class PaymentProcessingEvent implements DomainEvent {
    String transactionId;
    String providerId;
    Instant occurredAt;
    @Override public String getAggregateId() { return transactionId; }
    @Override public int getVersion() { return 2; }
    @Override public String getCorrelationId() { return null; }
}
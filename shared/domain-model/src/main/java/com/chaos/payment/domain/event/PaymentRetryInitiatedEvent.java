package com.chaos.payment.domain.event;
import lombok.Value;
import java.time.Instant;
@Value
public class PaymentRetryInitiatedEvent implements DomainEvent {
    String transactionId;
    int retryCount;
    String providerId;
    Instant occurredAt;
    @Override public String getAggregateId() { return transactionId; }
    @Override public int getVersion() { return 3; }
    @Override public String getCorrelationId() { return null; }
}
package com.chaos.payment.domain.event;
import lombok.Value;
import java.time.Instant;
@Value
public class FraudDetectedEvent implements DomainEvent {
    String transactionId;
    String reason;
    Instant occurredAt;
    @Override public String getAggregateId() { return transactionId; }
    @Override public int getVersion() { return 1; }
    @Override public String getCorrelationId() { return null; }
}
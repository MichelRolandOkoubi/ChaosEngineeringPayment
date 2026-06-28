package com.chaos.payment.domain.event;
import lombok.Value;
import java.time.Instant;
@Value
public class ChaosInjectedEvent implements DomainEvent {
    String transactionId;
    String scenario;
    Instant occurredAt;
    @Override public String getAggregateId() { return transactionId; }
    @Override public int getVersion() { return 0; }
    @Override public String getCorrelationId() { return null; }
}
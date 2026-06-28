package com.chaos.payment.domain.event;
import java.time.Instant;
public interface DomainEvent {
    String getAggregateId();
    int getVersion();
    Instant getOccurredAt();
    String getCorrelationId();
}
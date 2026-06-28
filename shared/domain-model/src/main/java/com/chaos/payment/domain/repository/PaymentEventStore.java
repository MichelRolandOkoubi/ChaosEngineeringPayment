package com.chaos.payment.domain.repository;
import com.chaos.payment.domain.event.DomainEvent;
import java.util.List;
public interface PaymentEventStore {
    void appendEvents(String aggregateId, List<DomainEvent> events, int expectedVersion);
    List<DomainEvent> getEvents(String aggregateId);
    List<DomainEvent> getEventsAfterVersion(String aggregateId, int version);
    void saveSnapshot(String aggregateId, Object snapshot, int version);
}
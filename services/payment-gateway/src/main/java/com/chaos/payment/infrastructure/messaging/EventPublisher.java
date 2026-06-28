package com.chaos.payment.infrastructure.messaging;
import com.chaos.payment.application.command.InitiatePaymentCommand;
import com.chaos.payment.domain.event.DomainEvent;
import java.util.List;
public interface EventPublisher {
    void publishAll(List<DomainEvent> events);
    void publish(DomainEvent event);
    void publishFallback(InitiatePaymentCommand command);
}
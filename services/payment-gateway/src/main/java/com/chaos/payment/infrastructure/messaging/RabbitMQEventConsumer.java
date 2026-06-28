package com.chaos.payment.infrastructure.messaging;

import com.chaos.payment.application.command.InitiatePaymentCommand;
import com.chaos.payment.application.command.handler.InitiatePaymentCommandHandler;
import com.chaos.payment.domain.event.DomainEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.runtime.annotations.RegisterForReflection;
import io.smallrye.reactive.messaging.annotations.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Acknowledgment;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.jboss.logging.Logger;

import java.util.concurrent.CompletionStage;

@ApplicationScoped
@RegisterForReflection
public class RabbitMQEventConsumer {

    private static final Logger LOG = Logger.getLogger(RabbitMQEventConsumer.class);

    @Inject
    InitiatePaymentCommandHandler commandHandler;

    @Inject
    ObjectMapper objectMapper;

    /**
     * Consume payment retry commands from RabbitMQ Dead Letter Queue
     */
    @Incoming("payment-retry-queue")
    @Acknowledgment(Acknowledgment.Strategy.MANUAL)
    @Blocking
    public CompletionStage<Void> processRetryCommand(Message<String> message) {
        try {
            LOG.infof("Processing retry command from RabbitMQ: %s", message.getPayload());

            InitiatePaymentCommand command = objectMapper.readValue(
                    message.getPayload(), InitiatePaymentCommand.class);

            return commandHandler.handle(command)
                    .thenCompose(result -> {
                        if (result.isSuccess()) {
                            return message.ack();
                        } else {
                            return message.nack(
                                    new RuntimeException("Retry failed: " + result.getMessage()));
                        }
                    });

        } catch (Exception e) {
            LOG.errorf(e, "Failed to process retry command");
            return message.nack(e);
        }
    }

    /**
     * Consume chaos notifications
     */
    @Incoming("chaos-notifications")
    @Blocking
    public void processChaosNotification(String payload) {
        try {
            LOG.warnf("🔥 Chaos notification received: %s", payload);
            // Handle chaos notifications (alerting, logging, etc.)
        } catch (Exception e) {
            LOG.errorf(e, "Failed to process chaos notification");
        }
    }

    /**
     * Consume payment events for projection updates (CQRS Read Model)
     */
    @Incoming("payment-events-consumer")
    @Blocking
    public void updateReadModel(String eventJson) {
        try {
            DomainEvent event = objectMapper.readValue(eventJson, DomainEvent.class);
            LOG.infof("Updating read model for event: %s", event.getClass().getSimpleName());

            // Update read model based on event type
            // This implements the CQRS read side

        } catch (Exception e) {
            LOG.errorf(e, "Failed to update read model");
        }
    }
}
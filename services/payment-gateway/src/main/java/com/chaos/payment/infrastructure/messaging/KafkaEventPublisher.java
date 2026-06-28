package com.chaos.payment.infrastructure.messaging;

import com.chaos.payment.application.command.InitiatePaymentCommand;
import com.chaos.payment.domain.event.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.jboss.logging.Logger;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class KafkaEventPublisher implements EventPublisher {

    private static final Logger LOG = Logger.getLogger(KafkaEventPublisher.class);

    @Inject
    @Channel("payment-events")
    Emitter<String> paymentEventsEmitter;

    @Inject
    @Channel("payment-commands")
    Emitter<String> paymentCommandsEmitter;

    @Inject
    @Channel("chaos-events")
    Emitter<String> chaosEventsEmitter;

    @Inject
    @Channel("fraud-alerts")
    Emitter<String> fraudAlertsEmitter;

    @Inject
    @Channel("payment-notifications")
    Emitter<String> notificationsEmitter;

    @Inject
    ObjectMapper objectMapper;

    @Override
    public void publishAll(List<DomainEvent> events) {
        events.forEach(this::publish);
    }

    @Override
    public void publish(DomainEvent event) {
        try {
            String eventJson = objectMapper.writeValueAsString(event);
            String eventType = event.getClass().getSimpleName();

            OutgoingKafkaRecordMetadata<String> metadata = OutgoingKafkaRecordMetadata
                    .<String>builder()
                    .withKey(event.getAggregateId())
                    .withHeaders(List.of(
                            new RecordHeader("event-type",
                                    eventType.getBytes(StandardCharsets.UTF_8)),
                            new RecordHeader("correlation-id",
                                    UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8)),
                            new RecordHeader("schema-version",
                                    "1.0".getBytes(StandardCharsets.UTF_8))))
                    .build();

            Message<String> message = Message.of(eventJson)
                    .addMetadata(metadata);

            // Route to appropriate topic
            if (event instanceof ChaosInjectedEvent) {
                chaosEventsEmitter.send(message);
                LOG.infof("Chaos event published: %s", eventType);
            } else if (event instanceof FraudDetectedEvent) {
                fraudAlertsEmitter.send(message);
                LOG.warnf("Fraud alert published: %s", eventType);
            } else {
                paymentEventsEmitter.send(message);
                LOG.infof("Payment event published: %s for aggregate: %s",
                        eventType, event.getAggregateId());
            }

        } catch (Exception e) {
            LOG.errorf(e, "Failed to publish event: %s", event.getClass().getSimpleName());
            throw new EventPublishException("Failed to publish event", e);
        }
    }

    @Override
    public void publishFallback(InitiatePaymentCommand command) {
        try {
            String commandJson = objectMapper.writeValueAsString(
                    new PaymentCommandFallbackEvent(command));

            OutgoingKafkaRecordMetadata<String> metadata = OutgoingKafkaRecordMetadata
                    .<String>builder()
                    .withKey(command.userId())
                    .build();

            paymentCommandsEmitter.send(
                    Message.of(commandJson).addMetadata(metadata));

            LOG.infof("Payment command queued for fallback processing: user=%s",
                    command.userId());

        } catch (Exception e) {
            LOG.errorf(e, "Failed to queue payment command for fallback");
        }
    }
}
package com.chaos.payment.infrastructure.messaging;

import com.chaos.payment.application.NotificationService;
import com.chaos.payment.domain.NotificationRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.reactive.messaging.annotations.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Acknowledgment;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.util.Set;
import java.util.concurrent.CompletionStage;

@ApplicationScoped
public class PaymentEventNotificationConsumer {

    private static final Logger LOG = Logger.getLogger(PaymentEventNotificationConsumer.class);

    private static final Set<String> NOTIFIABLE_EVENTS = Set.of(
            "PaymentCompletedEvent",
            "PaymentFailedEvent",
            "PaymentRefundedEvent"
    );

    @Inject
    NotificationService notificationService;

    @Inject
    ObjectMapper objectMapper;

    @Incoming("payment-notifications-in")
    @Acknowledgment(Acknowledgment.Strategy.MANUAL)
    @Blocking
    public CompletionStage<Void> onPaymentEvent(Message<String> message) {
        try {
            JsonNode event = objectMapper.readTree(message.getPayload());
            String eventType = event.path("eventType").asText();

            if (!NOTIFIABLE_EVENTS.contains(eventType)) {
                return message.ack();
            }

            NotificationRequest request = new NotificationRequest(
                    event.path("transactionId").asText(),
                    event.path("userId").asText(),
                    eventType,
                    new BigDecimal(event.path("amount").asText("0")),
                    event.path("currencyCode").asText(),
                    event.path("providerId").asText(),
                    resolveStatus(eventType),
                    event.path("phoneNumber").asText(null),
                    event.path("email").asText(null),
                    event.path("reason").asText(null),
                    null
            );

            notificationService.notify(request);
            return message.ack();

        } catch (Exception e) {
            LOG.errorf(e, "Failed to process payment notification event");
            return message.nack(e);
        }
    }

    @Incoming("fraud-alerts-notifications")
    @Acknowledgment(Acknowledgment.Strategy.MANUAL)
    @Blocking
    public CompletionStage<Void> onFraudAlert(Message<String> message) {
        try {
            JsonNode alert = objectMapper.readTree(message.getPayload());
            String verdict = alert.path("verdict").asText();

            if (!"BLOCKED".equals(verdict)) {
                return message.ack();
            }

            NotificationRequest request = new NotificationRequest(
                    alert.path("transactionId").asText(),
                    alert.path("userId").asText(),
                    "FraudDetectedEvent",
                    BigDecimal.ZERO,
                    null,
                    null,
                    "BLOCKED",
                    null,
                    null,
                    alert.path("reason").asText("Fraude détectée"),
                    null
            );

            notificationService.notify(request);
            return message.ack();

        } catch (Exception e) {
            LOG.errorf(e, "Failed to process fraud alert notification");
            return message.nack(e);
        }
    }

    private String resolveStatus(String eventType) {
        return switch (eventType) {
            case "PaymentCompletedEvent" -> "COMPLETED";
            case "PaymentFailedEvent" -> "FAILED";
            case "PaymentRefundedEvent" -> "REFUNDED";
            default -> "UNKNOWN";
        };
    }
}

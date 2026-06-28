package com.chaos.payment.infrastructure.messaging;

import com.chaos.payment.application.query.PaymentView;
import com.chaos.payment.infrastructure.persistence.PaymentProjectionRepository;
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
import java.time.Instant;
import java.util.concurrent.CompletionStage;

@ApplicationScoped
public class PaymentProjectionUpdater {

    private static final Logger LOG = Logger.getLogger(PaymentProjectionUpdater.class);

    @Inject
    PaymentProjectionRepository repository;

    @Inject
    ObjectMapper objectMapper;

    @Incoming("payment-events-query")
    @Acknowledgment(Acknowledgment.Strategy.MANUAL)
    @Blocking
    public CompletionStage<Void> onPaymentEvent(Message<String> message) {
        try {
            JsonNode event = objectMapper.readTree(message.getPayload());
            String eventType = event.path("eventType").asText();
            String transactionId = event.path("transactionId").asText();

            LOG.debugf("Updating projection for event %s on transaction %s", eventType, transactionId);

            switch (eventType) {
                case "PaymentInitiatedEvent" -> handleInitiated(event);
                case "PaymentProcessingEvent" -> updateStatus(transactionId, "PROCESSING");
                case "PaymentCompletedEvent" -> handleCompleted(event);
                case "PaymentFailedEvent" -> updateStatus(transactionId, "FAILED");
                case "PaymentRetryInitiatedEvent" -> updateStatus(transactionId, "RETRYING");
                case "PaymentRefundedEvent" -> updateStatus(transactionId, "REFUNDED");
                case "ChaosInjectedEvent" -> handleChaos(event);
                default -> LOG.debugf("Ignoring unknown event type: %s", eventType);
            }

            return message.ack();
        } catch (Exception e) {
            LOG.errorf(e, "Failed to update projection: %s", message.getPayload());
            return message.nack(e);
        }
    }

    private void handleInitiated(JsonNode event) {
        PaymentView view = new PaymentView(
                event.path("transactionId").asText(),
                event.path("userId").asText(),
                new BigDecimal(event.path("amount").asText("0")),
                event.path("currencyCode").asText(),
                event.path("providerId").asText(),
                null,
                "INITIATED",
                null,
                event.path("description").asText(null),
                null, null, 0, false, null, null,
                Instant.parse(event.path("occurredAt").asText(Instant.now().toString())),
                Instant.now()
        );
        repository.upsert(view);
    }

    private void updateStatus(String transactionId, String status) {
        repository.findById(transactionId).ifPresent(existing -> {
            PaymentView updated = new PaymentView(
                    existing.transactionId(), existing.userId(), existing.amount(),
                    existing.currencyCode(), existing.providerId(), existing.providerName(),
                    status, existing.paymentMethod(), existing.description(),
                    existing.externalReference(), existing.paymentUrl(),
                    existing.retryCount(), existing.chaosInjected(), existing.chaosScenario(),
                    existing.metadata(), existing.createdAt(), Instant.now()
            );
            repository.upsert(updated);
        });
    }

    private void handleCompleted(JsonNode event) {
        String transactionId = event.path("transactionId").asText();
        repository.findById(transactionId).ifPresent(existing -> {
            PaymentView updated = new PaymentView(
                    existing.transactionId(), existing.userId(), existing.amount(),
                    existing.currencyCode(), existing.providerId(), existing.providerName(),
                    "COMPLETED", existing.paymentMethod(), existing.description(),
                    event.path("externalReference").asText(null),
                    existing.paymentUrl(), existing.retryCount(),
                    existing.chaosInjected(), existing.chaosScenario(),
                    existing.metadata(), existing.createdAt(), Instant.now()
            );
            repository.upsert(updated);
        });
    }

    private void handleChaos(JsonNode event) {
        String transactionId = event.path("transactionId").asText();
        repository.findById(transactionId).ifPresent(existing -> {
            PaymentView updated = new PaymentView(
                    existing.transactionId(), existing.userId(), existing.amount(),
                    existing.currencyCode(), existing.providerId(), existing.providerName(),
                    existing.status(), existing.paymentMethod(), existing.description(),
                    existing.externalReference(), existing.paymentUrl(),
                    existing.retryCount(), true,
                    event.path("scenario").asText(null),
                    existing.metadata(), existing.createdAt(), Instant.now()
            );
            repository.upsert(updated);
        });
    }
}

package com.chaos.payment.infrastructure.messaging;

import com.chaos.payment.application.FraudDetectionService;
import com.chaos.payment.domain.FraudCheckRequest;
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
import java.util.concurrent.CompletionStage;

@ApplicationScoped
public class FraudEventConsumer {

    private static final Logger LOG = Logger.getLogger(FraudEventConsumer.class);

    @Inject
    FraudDetectionService fraudDetectionService;

    @Inject
    ObjectMapper objectMapper;

    @Incoming("payment-events-fraud")
    @Acknowledgment(Acknowledgment.Strategy.MANUAL)
    @Blocking
    public CompletionStage<Void> onPaymentInitiated(Message<String> message) {
        try {
            JsonNode event = objectMapper.readTree(message.getPayload());

            if (!"PaymentInitiatedEvent".equals(event.path("eventType").asText())) {
                return message.ack();
            }

            FraudCheckRequest request = new FraudCheckRequest(
                    event.path("transactionId").asText(),
                    event.path("userId").asText(),
                    new BigDecimal(event.path("amount").asText("0")),
                    event.path("currencyCode").asText(),
                    event.path("providerId").asText(),
                    event.path("ipAddress").asText(null),
                    event.path("deviceId").asText(null),
                    null
            );

            fraudDetectionService.analyze(request);
            return message.ack();

        } catch (Exception e) {
            LOG.errorf(e, "Failed to process payment event for fraud detection: %s",
                    message.getPayload());
            return message.nack(e);
        }
    }
}

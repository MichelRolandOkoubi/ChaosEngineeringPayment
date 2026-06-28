package com.chaos.payment.infrastructure.messaging;

import com.chaos.payment.application.ProcessPaymentCommand;
import com.chaos.payment.application.PaymentProcessingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.reactive.messaging.annotations.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Acknowledgment;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.jboss.logging.Logger;

import java.util.concurrent.CompletionStage;

@ApplicationScoped
public class PaymentCommandConsumer {

    private static final Logger LOG = Logger.getLogger(PaymentCommandConsumer.class);

    @Inject
    PaymentProcessingService processingService;

    @Inject
    ObjectMapper objectMapper;

    @Incoming("payment-commands")
    @Acknowledgment(Acknowledgment.Strategy.MANUAL)
    @Blocking
    public CompletionStage<Void> consume(Message<String> message) {
        String payload = message.getPayload();
        try {
            ProcessPaymentCommand command = objectMapper.readValue(payload, ProcessPaymentCommand.class);
            LOG.infof("Received payment command: transactionId=%s", command.transactionId());

            processingService.process(command)
                    .thenAccept(result -> LOG.infof("Processed payment %s: success=%b",
                            result.transactionId(), result.success()));

            return message.ack();
        } catch (Exception e) {
            LOG.errorf(e, "Failed to process payment command: %s", payload);
            return message.nack(e);
        }
    }

    @Incoming("payment-retry")
    @Acknowledgment(Acknowledgment.Strategy.MANUAL)
    @Blocking
    public CompletionStage<Void> consumeRetry(Message<String> message) {
        String payload = message.getPayload();
        try {
            ProcessPaymentCommand command = objectMapper.readValue(payload, ProcessPaymentCommand.class);
            LOG.infof("Retrying payment command: transactionId=%s, retry=%d",
                    command.transactionId(), command.retryCount());

            if (command.retryCount() >= 3) {
                LOG.warnf("Max retries reached for payment %s, sending to DLQ", command.transactionId());
                return message.ack();
            }

            processingService.process(command);
            return message.ack();
        } catch (Exception e) {
            LOG.errorf(e, "Failed to retry payment command: %s", payload);
            return message.nack(e);
        }
    }
}

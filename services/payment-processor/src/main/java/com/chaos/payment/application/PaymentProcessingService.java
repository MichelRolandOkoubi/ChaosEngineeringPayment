package com.chaos.payment.application;

import com.chaos.payment.infrastructure.provider.ProviderRoutingService;
import com.chaos.payment.infrastructure.provider.model.ProviderResponse;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.jboss.logging.Logger;

import java.util.concurrent.CompletableFuture;

@ApplicationScoped
public class PaymentProcessingService {

    private static final Logger LOG = Logger.getLogger(PaymentProcessingService.class);

    @Inject
    ProviderRoutingService routingService;

    @Inject
    MeterRegistry meterRegistry;

    @Inject
    @Channel("payment-events")
    Emitter<String> eventEmitter;

    @Inject
    com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    public CompletableFuture<PaymentCommandResult> process(ProcessPaymentCommand command) {
        return CompletableFuture.supplyAsync(() -> {
            Timer.Sample timer = Timer.start(meterRegistry);
            try {
                LOG.infof("Processing payment command: transactionId=%s, provider=%s, retry=%d",
                        command.transactionId(), command.providerId(), command.retryCount());

                ProviderResponse response = routingService.route(command);

                PaymentCommandResult result;
                if (response.isSuccess()) {
                    result = PaymentCommandResult.success(
                            command.transactionId(),
                            response.getExternalReference(),
                            response.getStatus(),
                            response.getPaymentUrl());
                    meterRegistry.counter("payment.processor.success",
                            "provider", command.providerId()).increment();
                } else {
                    result = PaymentCommandResult.failure(
                            command.transactionId(),
                            response.getErrorCode(),
                            response.getErrorMessage());
                    meterRegistry.counter("payment.processor.failure",
                            "provider", command.providerId(),
                            "error", response.getErrorCode()).increment();
                }

                publishResult(result);
                return result;

            } catch (Exception e) {
                LOG.errorf(e, "Unexpected error processing payment %s", command.transactionId());
                meterRegistry.counter("payment.processor.error",
                        "provider", command.providerId()).increment();
                return PaymentCommandResult.failure(command.transactionId(), "INTERNAL_ERROR", e.getMessage());
            } finally {
                timer.stop(meterRegistry.timer("payment.processor.duration",
                        "provider", command.providerId()));
            }
        });
    }

    private void publishResult(PaymentCommandResult result) {
        try {
            String json = objectMapper.writeValueAsString(result);
            eventEmitter.send(json);
        } catch (Exception e) {
            LOG.warnf(e, "Failed to publish result for transaction %s", result.transactionId());
        }
    }
}

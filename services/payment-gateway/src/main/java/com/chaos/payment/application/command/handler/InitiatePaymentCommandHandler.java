package com.chaos.payment.application.command.handler;

import com.chaos.payment.application.command.InitiatePaymentCommand;
import com.chaos.payment.application.command.PaymentCommandResult;
import com.chaos.payment.domain.model.Payment;
import com.chaos.payment.domain.repository.PaymentEventStore;
import com.chaos.payment.domain.service.FraudDetectionService;
import com.chaos.payment.domain.service.FraudAnalysis;
import com.chaos.payment.domain.service.ProviderRoutingService;
import com.chaos.payment.domain.service.ProviderResponse;
import com.chaos.payment.infrastructure.chaos.ChaosMonkey;
import com.chaos.payment.infrastructure.messaging.EventPublisher;
import com.chaos.payment.infrastructure.resilience.CircuitBreakerService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Fallback;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.jboss.logging.Logger;

import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@ApplicationScoped
public class InitiatePaymentCommandHandler {

    private static final Logger LOG = Logger.getLogger(InitiatePaymentCommandHandler.class);

    @Inject
    PaymentEventStore eventStore;

    @Inject
    FraudDetectionService fraudDetectionService;

    @Inject
    ProviderRoutingService providerRoutingService;

    @Inject
    EventPublisher eventPublisher;

    @Inject
    ChaosMonkey chaosMonkey;

    @Inject
    CircuitBreakerService circuitBreakerService;

    @Inject
    MeterRegistry meterRegistry;

    @Transactional
    @Retry(maxRetries = 3, delay = 1000, delayUnit = ChronoUnit.MILLIS, jitter = 200, retryOn = {
            RuntimeException.class }, abortOn = { IllegalArgumentException.class, SecurityException.class })
    @CircuitBreaker(requestVolumeThreshold = 10, failureRatio = 0.5, delay = 5000, successThreshold = 2)
    @Timeout(value = 25, unit = ChronoUnit.SECONDS)
    @Fallback(fallbackMethod = "handlePaymentFallback")
    public CompletableFuture<PaymentCommandResult> handle(InitiatePaymentCommand command) {

        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            LOG.infof("Processing payment command for provider: %s, user: %s",
                    command.providerId(), command.userId());

            // Chaos Engineering - Inject failures based on scenario
            chaosMonkey.maybeInjectFailure(command.providerId());

            // Fraud Detection
            FraudAnalysis fraudAnalysis = fraudDetectionService.analyze(command);
            if (fraudAnalysis.isFraudulent()) {
                LOG.warnf("Fraud detected for user %s: %s",
                        command.userId(), fraudAnalysis.getReason());
                return CompletableFuture.completedFuture(
                        PaymentCommandResult.rejected(fraudAnalysis.getReason()));
            }

            // Create Payment Aggregate
            Payment payment = Payment.create(
                    command.userId(),
                    command.amount(),
                    command.currencyCode(),
                    command.providerId(),
                    command.description(),
                    command.metadata());

            // Store events (Event Sourcing)
            eventStore.appendEvents(
                    payment.getTransactionId().getValue(),
                    payment.getUncommittedEvents(),
                    payment.getVersion());

            // Process payment through provider
            payment.processPayment();

            // Route to appropriate provider
            ProviderResponse response = providerRoutingService.route(payment, command);

            if (response.isSuccess()) {
                payment.completePayment(response.getExternalReference());
            } else {
                payment.failPayment(response.getErrorMessage(), response.getErrorCode());
            }

            // Persist updated events
            eventStore.appendEvents(
                    payment.getTransactionId().getValue(),
                    payment.getUncommittedEvents(),
                    payment.getVersion());
            payment.markEventsAsCommitted();

            // Publish events for read model (CQRS)
            eventPublisher.publishAll(payment.getUncommittedEvents());

            // Metrics
            meterRegistry.counter("payment.processed",
                    "provider", command.providerId(),
                    "status", payment.getStatus().name()).increment();

            sample.stop(meterRegistry.timer("payment.processing.duration",
                    "provider", command.providerId()));

            return CompletableFuture.completedFuture(
                    PaymentCommandResult.success(payment.getTransactionId().getValue()));

        } catch (Exception e) {
            LOG.errorf(e, "Payment processing failed for user: %s", command.userId());

            meterRegistry.counter("payment.error",
                    "provider", command.providerId(),
                    "error", e.getClass().getSimpleName()).increment();

            sample.stop(meterRegistry.timer("payment.processing.duration",
                    "provider", command.providerId(),
                    "outcome", "error"));

            throw e;
        }
    }

    public CompletableFuture<PaymentCommandResult> handlePaymentFallback(
            InitiatePaymentCommand command) {

        LOG.warnf("Payment fallback triggered for provider: %s, user: %s",
                command.providerId(), command.userId());

        // Store in fallback queue for retry
        meterRegistry.counter("payment.fallback",
                "provider", command.providerId()).increment();

        // Queue for async processing
        eventPublisher.publishFallback(command);

        return CompletableFuture.completedFuture(
                PaymentCommandResult.queued(
                        "Payment queued for processing. Provider temporarily unavailable."));
    }
}
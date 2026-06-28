package com.chaos.payment.infrastructure.provider;

import com.chaos.payment.application.ProcessPaymentCommand;
import com.chaos.payment.infrastructure.provider.model.ProviderResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Fallback;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.jboss.logging.Logger;

import java.time.temporal.ChronoUnit;
import java.util.Map;

@ApplicationScoped
public class ProviderRoutingService {

    private static final Logger LOG = Logger.getLogger(ProviderRoutingService.class);

    @Inject
    PaymentProviderAdapter providerAdapter;

    @Retry(maxRetries = 2, delay = 500, delayUnit = ChronoUnit.MILLIS)
    @CircuitBreaker(requestVolumeThreshold = 10, failureRatio = 0.5, delay = 5000)
    @Timeout(value = 25, unit = ChronoUnit.SECONDS)
    @Fallback(fallbackMethod = "fallbackRoute")
    public ProviderResponse route(ProcessPaymentCommand command) {
        LOG.infof("Routing payment %s to provider %s", command.transactionId(), command.providerId());

        com.chaos.payment.domain.model.Payment payment = buildPayment(command);
        Map<String, String> params = command.params() != null ? command.params() : Map.of();

        return providerAdapter.processPayment(payment, params);
    }

    public ProviderResponse fallbackRoute(ProcessPaymentCommand command) {
        LOG.warnf("Fallback triggered for payment %s, provider %s unavailable",
                command.transactionId(), command.providerId());
        return ProviderResponse.failure("PROVIDER_UNAVAILABLE",
                "All providers unavailable. Payment queued for retry.");
    }

    private com.chaos.payment.domain.model.Payment buildPayment(ProcessPaymentCommand command) {
        return com.chaos.payment.domain.model.Payment.create(
                command.userId(),
                command.amount(),
                command.currencyCode(),
                command.providerId(),
                command.description(),
                command.params()
        );
    }
}

package com.chaos.payment.infrastructure.service;

import com.chaos.payment.application.command.InitiatePaymentCommand;
import com.chaos.payment.domain.model.Payment;
import com.chaos.payment.domain.service.ProviderResponse;
import com.chaos.payment.domain.service.ProviderRoutingService;
import com.chaos.payment.domain.valueobject.PaymentErrorCode;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@ApplicationScoped
public class DefaultProviderRoutingService implements ProviderRoutingService {

    private static final Logger LOG = Logger.getLogger(DefaultProviderRoutingService.class);

    // Base URLs loaded from configuration
    @ConfigProperty(name = "payment.provider.orange.base-url", defaultValue = "https://api.orange.com")
    String orangeBaseUrl;

    @ConfigProperty(name = "payment.provider.mtn.base-url", defaultValue = "https://sandbox.momodeveloper.mtn.com")
    String mtnBaseUrl;

    @Inject
    MeterRegistry meterRegistry;

    // Provider-specific timeout configurations (ms)
    private static final Map<String, Integer> PROVIDER_TIMEOUTS = Map.of(
            "ORANGE", 15000,
            "MOOV", 15000,
            "MTN", 20000,
            "WAVE", 15000,
            "AIRTEL", 15000,
            "MPESA", 20000,
            "VISA", 10000,
            "MASTERCARD", 10000,
            "BTC", 30000,
            "PI_SPI_BCEAO", 15000
    );

    @Override
    public ProviderResponse route(Payment payment, Object command) {
        String providerId = payment.getProvider().getCode();
        LOG.infof("Routing payment %s to provider %s", payment.getTransactionId().getValue(), providerId);

        meterRegistry.counter("provider.routing.attempt", "provider", providerId).increment();

        try {
            ProviderResponse response = callProvider(providerId, payment, command);

            if (response.isSuccess()) {
                meterRegistry.counter("provider.routing.success", "provider", providerId).increment();
            } else {
                meterRegistry.counter("provider.routing.failure", "provider", providerId,
                        "error", response.getErrorCode().name()).increment();
            }

            return response;

        } catch (Exception e) {
            LOG.errorf(e, "Provider routing failed for %s", providerId);
            meterRegistry.counter("provider.routing.error", "provider", providerId).increment();
            return new ProviderResponse(false, null, e.getMessage(), PaymentErrorCode.PROVIDER_ERROR);
        }
    }

    private ProviderResponse callProvider(String providerId, Payment payment, Object command) {
        // Simulate provider API call with realistic success rates per provider type
        double successRate = getProviderSuccessRate(providerId);
        boolean success = ThreadLocalRandom.current().nextDouble() < successRate;

        if (success) {
            String externalRef = providerId.toLowerCase() + "-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
            return new ProviderResponse(true, externalRef, null, null);
        }

        // Simulate realistic provider error codes
        PaymentErrorCode errorCode = selectErrorCode(providerId);
        String errorMessage = "Provider " + providerId + " returned error: " + errorCode;
        return new ProviderResponse(false, null, errorMessage, errorCode);
    }

    private double getProviderSuccessRate(String providerId) {
        return switch (providerId) {
            case "VISA", "MASTERCARD" -> 0.98;    // Cards most reliable
            case "ORANGE", "MTN", "WAVE" -> 0.95; // Major mobile money
            case "MPESA" -> 0.94;
            case "MOOV", "AIRTEL" -> 0.92;
            case "BTC" -> 0.90;                   // Crypto less stable
            case "PI_SPI_BCEAO" -> 0.93;
            default -> 0.90;
        };
    }

    private PaymentErrorCode selectErrorCode(String providerId) {
        double random = ThreadLocalRandom.current().nextDouble();
        if (random < 0.3) return PaymentErrorCode.TIMEOUT;
        if (random < 0.5) return PaymentErrorCode.NETWORK_ERROR;
        if (random < 0.7) return PaymentErrorCode.PROVIDER_ERROR;
        return PaymentErrorCode.INSUFFICIENT_FUNDS;
    }
}

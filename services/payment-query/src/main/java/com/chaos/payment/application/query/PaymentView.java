package com.chaos.payment.application.query;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.quarkus.runtime.annotations.RegisterForReflection;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@RegisterForReflection
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PaymentView(
        String transactionId,
        String userId,
        BigDecimal amount,
        String currencyCode,
        String providerId,
        String providerName,
        String status,
        String paymentMethod,
        String description,
        String externalReference,
        String paymentUrl,
        int retryCount,
        boolean chaosInjected,
        String chaosScenario,
        Map<String, String> metadata,
        Instant createdAt,
        Instant updatedAt
) {}

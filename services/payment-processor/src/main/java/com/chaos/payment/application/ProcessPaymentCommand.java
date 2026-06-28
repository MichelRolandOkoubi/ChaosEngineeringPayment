package com.chaos.payment.application;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;

import java.math.BigDecimal;
import java.util.Map;

@RegisterForReflection
public record ProcessPaymentCommand(
        @JsonProperty("transactionId") String transactionId,
        @JsonProperty("userId") String userId,
        @JsonProperty("amount") BigDecimal amount,
        @JsonProperty("currencyCode") String currencyCode,
        @JsonProperty("providerId") String providerId,
        @JsonProperty("description") String description,
        @JsonProperty("params") Map<String, String> params,
        @JsonProperty("correlationId") String correlationId,
        @JsonProperty("retryCount") int retryCount
) {}

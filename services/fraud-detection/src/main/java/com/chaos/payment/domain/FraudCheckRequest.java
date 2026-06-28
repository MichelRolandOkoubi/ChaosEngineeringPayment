package com.chaos.payment.domain;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.math.BigDecimal;
import java.util.Map;

@RegisterForReflection
public record FraudCheckRequest(
        String transactionId,
        String userId,
        BigDecimal amount,
        String currencyCode,
        String providerId,
        String ipAddress,
        String deviceId,
        Map<String, String> metadata
) {}

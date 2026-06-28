package com.chaos.payment.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.quarkus.runtime.annotations.RegisterForReflection;

import java.math.BigDecimal;
import java.util.Map;

@RegisterForReflection
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NotificationRequest(
        String transactionId,
        String userId,
        String eventType,
        BigDecimal amount,
        String currencyCode,
        String providerId,
        String status,
        String phoneNumber,
        String email,
        String reason,
        Map<String, String> metadata
) {}

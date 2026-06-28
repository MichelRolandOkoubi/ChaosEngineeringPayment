package com.chaos.payment.application.query;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record GetPaymentQuery(String transactionId) {}

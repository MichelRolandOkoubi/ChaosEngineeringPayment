package com.chaos.payment.application.query;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record ListPaymentsQuery(
        String userId,
        String providerId,
        String status,
        int page,
        int size
) {
    public ListPaymentsQuery {
        if (page < 0) page = 0;
        if (size <= 0 || size > 100) size = 20;
    }
}

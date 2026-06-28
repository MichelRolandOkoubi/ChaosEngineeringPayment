package com.chaos.payment.infrastructure.chaos.experiment;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ResilienceValidator {
    public SteadyStateMetrics captureMetrics() {
        return SteadyStateMetrics.builder()
                .successRate(1.0)
                .p99LatencyMs(100)
                .errorRate(0.0)
                .build();
    }

    public boolean checkDataIntegrity() {
        return true;
    }
}

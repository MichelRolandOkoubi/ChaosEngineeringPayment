package com.chaos.payment.infrastructure.chaos.experiment;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SteadyStateMetrics {
    double successRate;
    long p99LatencyMs;
    double errorRate;
}

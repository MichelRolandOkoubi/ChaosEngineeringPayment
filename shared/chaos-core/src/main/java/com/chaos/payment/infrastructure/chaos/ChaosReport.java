package com.chaos.payment.infrastructure.chaos;

import lombok.Builder;
import lombok.Value;

import java.util.Map;

@Value
@Builder
public class ChaosReport {
    int totalInjections;
    Map<String, Integer> providerFailureCounts;
    String activeScenario;
    boolean chaosEnabled;
    double failureRate;
}

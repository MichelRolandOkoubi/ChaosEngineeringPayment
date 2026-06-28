package com.chaos.payment.infrastructure.chaos.experiment;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ChaosExperimentResult {
    String experimentId;
    String name;
    SteadyStateMetrics initialState;
    SteadyStateMetrics duringState;
    SteadyStateMetrics recoveredState;
    boolean passed;
    long failoverTime;
}
